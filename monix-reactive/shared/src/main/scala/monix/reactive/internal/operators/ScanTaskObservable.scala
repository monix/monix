/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.reactive.internal.operators

import monix.eval.{Callback, Task}
import monix.execution.Ack.Stop
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.cancelables.OrderedCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.Future

/** Implementation for `Observable.scanTask`.
  *
  * Implementation is based on [[MapTaskObservable]].
  *
  * Tricky concurrency handling within, here be dragons!
  */
private[reactive] final class ScanTaskObservable[A, S](
  source: Observable[A],
  seed: Task[S],
  op: (S, A) => Task[S])
  extends Observable[S] {

  def unsafeSubscribeFn(out: Subscriber[S]): Cancelable = {
    val conn = OrderedCancelable()

    val cb = new Callback[S] {
      def onSuccess(initial: S): Unit = {
        val subscriber = new ScanTaskSubscriber(out, initial)
        val mainSubscription = source.unsafeSubscribeFn(subscriber)
        val c = Cancelable { () =>
          try mainSubscription.cancel()
          finally subscriber.cancel()
        }
        conn.orderedUpdate(c, 2)
      }

      def onError(ex: Throwable): Unit =
        out.onError(ex)
    }

    conn.orderedUpdate(seed.runAsync(cb)(out.scheduler), 1)
  }

  private final class ScanTaskSubscriber(out: Subscriber[S], initial: S)
    extends Subscriber[A] with Cancelable { self =>

    import MapTaskObservable.MapTaskState
    import MapTaskObservable.MapTaskState._

    implicit val scheduler = out.scheduler

    // For synchronizing our internal state machine, padded
    // in order to avoid the false sharing problem
    private[this] val stateRef = Atomic.withPadding(WaitOnNext : MapTaskState, LeftRight128)

    // Boolean for keeping the `isActive` state, needed because we could miss
    // out on seeing a `Cancelled` state due to the `lazySet` instructions,
    // making the visibility of the `Cancelled` state thread-unsafe!
    private[this] val isActive = Atomic(true)

    // Current state, keeps getting updated by the task in `onNext`
    private[this] var currentS = initial

    /** For canceling the current active task, in case there is any. Here
      * we can afford a `compareAndSet`, not being a big deal since
      * cancellation only happens once.
      */
    def cancel(): Unit = {
      // The cancellation is a two-phase process, because usage of the
      // `Cancelled` state alone is thread-unsafe
      if (isActive.getAndSet(false)) cancelState()
    }

    @tailrec private def cancelState(): Unit =
      stateRef.get match {
        case current @ Active(ref) =>
          if (stateRef.compareAndSet(current, Cancelled)) {
            ref.cancel()
          } else {
            // $COVERAGE-OFF$
            cancelState() // retry
            // $COVERAGE-ON$
          }
        case current @ WaitComplete(_, ref) =>
          if (ref != null) {
            if (stateRef.compareAndSet(current, Cancelled)) {
              ref.cancel()
            } else {
              // $COVERAGE-OFF$
              cancelState() // retry
              // $COVERAGE-ON$
            }
          }
        case current @ (WaitOnNext | WaitActiveTask) =>
          if (!stateRef.compareAndSet(current, Cancelled)) {
            // $COVERAGE-OFF$
            cancelState() // retry
            // $COVERAGE-ON$
          }
        case Cancelled =>
          // $COVERAGE-OFF$
          () // do nothing else
          // $COVERAGE-ON$
      }

    def onNext(elem: A): Future[Ack] = {
      // For protecting against user code, without violating the
      // observer's contract, by marking the boundary after which
      // we can no longer stream errors downstream
      var streamErrors = true

      // WARN: Concurrent cancellation might have happened, due
      // to the `Cancelled` state being thread-unsafe because of
      // the logic using `lazySet` below; hence the extra check
      if (!isActive.get) {
        Stop
      } else try {
        val task = op(currentS, elem).transformWith(childOnSuccess, childOnError)
        // No longer allowed to stream errors downstream
        streamErrors = false

        // Simple, ordered write - we cannot use WaitOnNext as the start of
        // an iteration because we couldn't detect synchronous execution below.
        // WARN: this can override the `Cancelled` status!
        stateRef.lazySet(WaitActiveTask)

        // Start execution
        val ack = task.runAsync

        // This `getAndSet` is concurrent with the task being finished
        // (the `getAndSet` in the Task.flatMap above), but not with
        // the `getAndSet` happening in `onComplete` and `onError`,
        // therefore a `WaitComplete` state is invalid here. The state
        // we do expect most of the time is either `WaitOnNext` or
        // `WaitActiveTask`.
        stateRef.getAndSet(Active(ack)) match {
          case WaitOnNext =>
            // Task execution was synchronous, w00t, so redo state!
            //
            // NOTE: we don't need to worry about cancellation here, b/c we
            // have no child active and the cancellation of the parent stream
            // is not our concern
            stateRef.lazySet(WaitOnNext)
            ack.syncTryFlatten

          case WaitActiveTask =>
            // Expected outcome for async observables ...
            //
            // Concurrent cancellation might have happened, the `Cancelled` state
            // being thread-unsafe, hence this check;
            //
            // WARN: the assumption is that if the `Cancelled` state was set
            // right before `lazySet(WaitActiveTask)`, then we would see
            // `isActive == false` here b/c it was updated before `stateRef` (JMM);
            // And if `stateRef = Cancelled` happened afterwards, then we should
            // see it in the outer match statement
            if (isActive.get) {
              ack
            } else {
              cancelState()
              Stop
            }

          case WaitComplete(_,_) =>
            // Branch that can happen in case the child has finished
            // already in error, so stop further onNext events.
            stateRef.lazySet(Cancelled) // GC purposes
            Stop

          case Cancelled =>
            // Race condition, oops, revert
            cancelState()
            Stop

          case state @ Active(_) =>
            // This should never, ever happen!
            // Something is screwed up in our state machine :-(
            reportInvalidState(state, "onNext")
            Stop
        }
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors) {
            onError(ex)
            Stop
          } else {
            scheduler.reportFailure(ex)
            Stop
          }
      }
    }

    // Reusable function reference, to prevent creating a new instance
    // on each `onNext` / `transformWith` call below
    private[this] val childOnSuccess = (value: S) => {
      // Updating mutable shared state, no need for synchronization
      // because `onNext` operations are ordered
      currentS = value
      // Shoot first, ask questions later :-)
      val next = out.onNext(value)

      // This assignment must happen after `onNext`, otherwise
      // we can end with a race condition with `onComplete`
      stateRef.getAndSet(WaitOnNext) match {
        case WaitActiveTask | WaitOnNext | Active(_) =>
          // Expected outcome
          Task.fromFuture(next)

        case Cancelled =>
          Task.now(Stop)

        case WaitComplete(exOpt, _) =>
          // An `onComplete` or `onError` event happened since
          // `onNext` was called, so we are now responsible for
          // signaling it downstream.  Note that we've set
          // `WaitOnNext` above, which would make one wonder if
          // we couldn't have a problem with the logic in
          // `onComplete` or `onError`, but if we are seeing
          // this state, it means that these calls already
          // happened, so we can't have a race condition.
          exOpt match {
            case None => out.onComplete()
            case Some(ex) => out.onError(ex)
          }
          Task.now(Stop)
      }
    }

    // Reusable function reference, to prevent creating a new instance
    // on each `onNext` / `transformWith` call below
    private[this] val childOnError = (error: Throwable) => {
      // The cancelable passed in WaitComplete here can be `null`
      // because it would only replace the child's own cancelable
      stateRef.getAndSet(WaitComplete(Some(error), null)) match {
        case WaitActiveTask | WaitOnNext | Active(_) =>
          Task.eval {
            out.onError(error)
            Stop
          }

        case WaitComplete(otherEx, _) =>
          // If an exception also happened on main observable, log it!
          otherEx.foreach(scheduler.reportFailure)
          // Send our immediate error downstream and stop everything
          out.onError(error)
          Task.now(Stop)

        case Cancelled =>
          // User cancelled, but we have to log it somewhere
          scheduler.reportFailure(error)
          Task.now(Stop)
      }
    }

    private def signalFinish(ex: Option[Throwable]): Unit = {
      // It's fine to fetch the current cancelable like this because
      // this can only give us the cancelable of the active child and
      // the only race condition that can happen is for the child to
      // set this to `null` between this `get` and the upcoming
      // `getAndSet`, which is totally fine
      val childRef = stateRef.get match {
        case Active(ref) => ref
        case WaitComplete(_,ref) => ref
        case _ => null
      }

      // Can have a race condition with the `onComplete` / `onError`
      // signal in the child, but this works fine because of the
      // no-concurrent clause in the protocol of communication. So
      // either we have exactly one active child, in which case it
      // will be responsible for sending the final signal, or we don't
      // have any active child, in which case it is the responsibility
      // of the main subscriber to do it right here
      stateRef.getAndSet(WaitComplete(ex, childRef)) match {
        case WaitOnNext =>
          // In this state we know we have no active task,
          // and we know that the last `onNext` was triggered
          // (but its future not necessarily finished)
          // so we can just signal the completion event
          if (ex.isEmpty) out.onComplete() else out.onError(ex.get)
          // GC purposes: we no longer need the cancelable reference!
          stateRef.lazySet(Cancelled)

        case WaitComplete(_,_) =>
          // This branch happens if the child has triggered the completion
          // event already, thus there's nothing for us left to do.
          // GC purposes: we no longer need `childRef`.
          stateRef.lazySet(Cancelled)

        case Cancelled =>
          // Oops, concurrent cancellation happened
          cancelState()
          // GC purposes: we no longer need `childRef`
          stateRef.lazySet(Cancelled)

        case Active(_) =>
          // On this branch we've got an active child that needs to finish.
          //
          // WARN: Concurrent cancellation might have happened and because the
          // `Cancelled` state is thread unsafe, we need a second check.
          // Assumption is that `isActive = false` would be visible in case of
          // a race condition!
          if (!isActive.get) cancelState()

        case WaitActiveTask =>
          // Something is screwed up in our state machine :-(
          // $COVERAGE-OFF$
          reportInvalidState(WaitActiveTask, "signalFinish")
        // $COVERAGE-ON$
      }
    }

    def onComplete(): Unit =
      signalFinish(None)
    def onError(ex: Throwable): Unit =
      signalFinish(Some(ex))

    private def reportInvalidState(state: MapTaskState, method: String): Unit = {
      // $COVERAGE-OFF$
      cancelState()
      scheduler.reportFailure(
        new IllegalStateException(
          s"State $state in the Monix MapTask.$method implementation is invalid, " +
            "due to either a broken Subscriber implementation, or a bug, " +
            "please open an issue, see: https://monix.io"
        ))
      // $COVERAGE-ON$
    }
  }
}
