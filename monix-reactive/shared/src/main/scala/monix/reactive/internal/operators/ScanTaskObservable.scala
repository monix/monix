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
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.annotation.tailrec
import scala.concurrent.Future

/** Implementation for `Observable.scanTask`.
  *
  * Implementation is based on [[ScanTaskObservable]].
  *
  * Tricky concurrency handling within, here be dragons!
  * See the description on [[ScanTaskObservable]] for details.
  */
private[reactive] final class ScanTaskObservable[A, S]
  (source: Observable[A], seed: Task[S], op: (S, A) => Task[S])
  extends Observable[S] {

  def unsafeSubscribeFn(out: Subscriber[S]): Cancelable = {
    val conn = MultiAssignmentCancelable()

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
    private[this] val stateRef =
      Atomic.withPadding(WaitOnNext : MapTaskState, LeftRight128)

    // Current state, keeps getting updated by the task in `onNext`
    private[this] var currentS = initial

    /** For canceling the current active task, in case there is any. Here
      * we can afford a `compareAndSet`, not being a big deal since
      * cancellation only happens once.
      */
    @tailrec def cancel(): Unit = {
      stateRef.get match {
        case current @ Active(ref) =>
          if (stateRef.compareAndSet(current, Cancelled))
            ref.cancel()
          // $COVERAGE-OFF$
          else
            cancel() // retry
          // $COVERAGE-ON$
        case current @ WaitComplete(_, ref) =>
          if (ref != null) {
            if (stateRef.compareAndSet(current, Cancelled))
              ref.cancel()
            // $COVERAGE-OFF$
            else
              cancel() // retry
            // $COVERAGE-ON$
          }
        case current @ (WaitOnNext | WaitActiveTask) =>
          if (!stateRef.compareAndSet(current, Cancelled))
            // $COVERAGE-OFF$
            cancel() // retry
            // $COVERAGE-ON$
        case Cancelled =>
          () // do nothing else
      }
    }

    def onNext(elem: A): Future[Ack] = {
      // For protecting against user code, without violating the
      // observer's contract, by marking the boundary after which
      // we can no longer stream errors downstream
      var streamErrors = true
      try {
        val task = op(currentS, elem).transformWith(
          value => {
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
          },
          error => {
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
        )

        // No longer allowed to stream errors downstream
        streamErrors = false
        // Simple, ordered write - we cannot use WaitOnNext as the
        // start of an iteration because we couldn't detect
        // synchronous execution below
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
            stateRef.lazySet(WaitOnNext)
            ack.syncTryFlatten

          case WaitActiveTask =>
            // Expected outcome for asynchronous tasks
            ack

          case WaitComplete(_,_) =>
            // Branch that can happen in case the child has finished
            // already in error, so stop further onNext events.
            Stop

          case Cancelled =>
            // Race condition, oops, now cancel the active task
            ack.cancel()
            // Now restore the state and pretend that this didn't
            // happen :-) Note that this is probably going to call
            // `ack.cancel()` a second time, but that's OK
            self.cancel()
            Stop

          case state @ Active(_) =>
            // $COVERAGE-OFF$
            // This should never, ever happen!
            // Something is screwed up in our state machine :-(
            reportInvalidState(state, "onNext")
            Stop
            // $COVERAGE-ON$
        }
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors) {
            onError(ex)
            Stop
          } else {
            // $COVERAGE-OFF$
            scheduler.reportFailure(ex)
            Stop
            // $COVERAGE-ON$
          }
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

        case previous @ (WaitComplete(_,_) | Cancelled) =>
          // GC purposes: we no longer need the cancelable reference!
          stateRef.lazySet(previous)

        case Active(_) =>
          // Child still active, so we are not supposed to do anything here
          // since it's now the responsibility of the child to finish!
          ()

        case WaitActiveTask =>
          // Something is screwed up in our state machine :-(
          reportInvalidState(WaitActiveTask, "signalFinish")
      }
    }

    def onComplete(): Unit =
      signalFinish(None)
    def onError(ex: Throwable): Unit =
      signalFinish(Some(ex))

    // $COVERAGE-OFF$
    private def reportInvalidState(state: MapTaskState, method: String): Unit = {
      scheduler.reportFailure(
        new IllegalStateException(
          s"State $state in the Monix ScanAsync.$method implementation is invalid, " +
          "due to either a broken Subscriber implementation, or a bug, " +
          "please open an issue, see: https://monix.io"
        ))
    }
    // $COVERAGE-ON$
  }
}