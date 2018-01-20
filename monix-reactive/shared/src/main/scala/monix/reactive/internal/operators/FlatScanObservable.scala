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

import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.execution.exceptions.CompositeException
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Failure

/** Implementation for `Observable.scanTask`.
  *
  * Implementation is based on [[MapTaskObservable]].
  *
  * Tricky concurrency handling within, here be dragons!
  */
private[reactive] final class FlatScanObservable[A,R](
  source: Observable[A],
  initial: () => R,
  op: (R, A) => Observable[R], delayErrors: Boolean)
  extends Observable[R] {

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    var streamErrors = true
    try {
      val value = initial()
      streamErrors = false
      subscribeWithState(out, value)
    } catch {
      case NonFatal(ex) if streamErrors =>
        out.onError(ex)
        Cancelable.empty
    }
  }

  def subscribeWithState(out: Subscriber[R], initial: R): Cancelable = {
    val subscriber = new FlatScanSubscriber(out, initial)
    val mainSubscription = source.unsafeSubscribeFn(subscriber)

    Cancelable { () =>
      try mainSubscription.cancel()
      finally subscriber.cancel()
    }
  }

  private final class FlatScanSubscriber(out: Subscriber[R], initial: R)
    extends Subscriber[A] with Cancelable { self =>

    import ConcatMapObservable.FlatMapState
    import ConcatMapObservable.FlatMapState._

    implicit val scheduler = out.scheduler

    // For gathering errors
    private[this] val errors =
      if (delayErrors) Atomic(List.empty[Throwable])
      else null

    // Boolean for keeping the `isActive` state, needed because we could miss
    // out on seeing a `Cancelled` state due to the `lazySet` instructions,
    // making the visibility of the `Cancelled` state thread-unsafe!
    private[this] val isActive = Atomic(true)

    // For synchronizing our internal state machine, padded
    // in order to avoid the false sharing problem
    private[this] val stateRef = Atomic.withPadding(
      WaitOnNextChild(Continue) : FlatMapState,
      LeftRight128)

    // Mutable reference to the current state
    private[this] var currentState = initial

    /** For canceling the current active task, in case there is any. Here
      * we can afford a `compareAndSet`, not being a big deal since
      * cancellation only happens once.
      */
    def cancel(): Unit = {
      // The cancellation is a two-phase process
      if (isActive.getAndSet(false)) cancelState()
    }

    @tailrec private def cancelState(): Unit = {
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
        case current @ (WaitOnNextChild(_) | WaitOnActiveChild) =>
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
        val asyncUpstreamAck = Promise[Ack]()
        val child = op(currentState, elem)
        // No longer allowed to stream errors downstream
        streamErrors = false

        // Simple, ordered write - we cannot use WaitOnNext as the start of an
        // iteration because we cannot detect synchronous execution below;
        // WARN: this can override the `Cancelled` status!
        stateRef.lazySet(WaitOnActiveChild)

        // Shoot first, ask questions later :-)
        val cancellable = child.unsafeSubscribeFn(new ChildSubscriber(out, asyncUpstreamAck))

        // Execution already started at this point This `getAndSet` is
        // concurrent with the task being finished (the `getAndSet` in
        // the Task.flatMap above), but not with the `getAndSet`
        // happening in `onComplete` and `onError`, therefore a
        // `WaitComplete` state is invalid here. The state we do
        // expect most of the time is either `WaitOnNext` or
        // `WaitActiveTask`.
        stateRef.getAndSet(Active(cancellable)) match {
          case previous @ WaitOnNextChild(ack) =>
            // Task execution was synchronous, w00t, so redo state!
            //
            // NOTE: we don't need to worry about cancellation here, b/c we
            // have no child active and the cancellation of the parent stream
            // is not our concern
            stateRef.lazySet(previous)
            ack.syncTryFlatten

          case WaitOnActiveChild =>
            // Expected outcome for async observables ...
            //
            // Concurrent cancellation might have happened, the `Cancelled` state
            // being thread-unsafe, hence this check;
            //
            // WARN: the assumption is that if the `Cancelled` state was set
            // right before `lazySet(WaitOnActiveChild)`, then we would see
            // `isActive == false` here b/c it was updated before `stateRef` (JMM);
            // And if `stateRef = Cancelled` happened afterwards, then we should
            // see it in the outer match statement
            if (isActive.get) {
              asyncUpstreamAck.future.syncTryFlatten
            } else {
              cancelState()
              Stop
            }

          case WaitComplete(_, _) =>
            // Branch that can happen in case the child has finished
            // already in error, so stop further onNext events.
            stateRef.lazySet(Cancelled) // GC purposes
            Stop

          case Cancelled =>
            // Race condition, oops, now cancel the active task
            cancelState()
            Stop

          case state @ Active(_) =>
            // This should never, ever happen!
            // Something is screwed up in our state machine :-(
            // $COVERAGE-OFF$
            reportInvalidState(state, "onNext")
            Stop
          // $COVERAGE-ON$
        }
      } catch { case ex if NonFatal(ex) =>
        if (streamErrors) {
          onError(ex)
          Stop
        } else {
          scheduler.reportFailure(ex)
          Stop
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
        case WaitOnNextChild(_) =>
          // In this state we know we have no active task, so we are
          // free to signal the final event
          if (ex.isEmpty) sendOnComplete() else out.onError(ex.get)
          // GC purposes: we no longer need the cancelable reference!
          stateRef.lazySet(Cancelled)

        case Active(_) =>
          // On this branch we've got an active child that needs to finish.
          //
          // WARN: Concurrent cancellation might have happened and because the
          // `Cancelled` state is thread unsafe, we need a second check.
          // Assumption is that `isActive = false` would be visible in case of
          // a race condition!
          if (!isActive.get) cancelState()

        case WaitComplete(_,_) =>
          // This branch happens if the child has triggered the completion
          // event already, thus there's nothing for us left to do.
          // GC purposes: we no longer need `childRef`.
          stateRef.lazySet(Cancelled)

        case Cancelled =>
          // Oops, cancellation happened, cancel!
          cancelState()
          // GC purposes: we no longer need `childRef`.
          stateRef.lazySet(Cancelled)

        case WaitOnActiveChild =>
          // Something is screwed up in our state machine :-(
          // $COVERAGE-OFF$
          reportInvalidState(WaitOnActiveChild, "signalFinish")
        // $COVERAGE-ON$
      }
    }

    def onComplete(): Unit =
      signalFinish(None)

    def onError(ex: Throwable): Unit =
      if (!delayErrors) signalFinish(Some(ex)) else {
        errors.transform(list => ex :: list)
        signalFinish(None)
      }

    private def sendOnComplete(): Unit = {
      if (!delayErrors) out.onComplete() else
        this.errors.get match {
          case Nil => out.onComplete()
          case list => out.onError(CompositeException(list))
        }
    }

    private def reportInvalidState(state: FlatMapState, method: String): Unit = {
      // $COVERAGE-OFF$
      cancelState()
      scheduler.reportFailure(
        new IllegalStateException(
          s"State $state in the Monix ConcatMap.$method implementation is invalid, " +
            "due to either a broken Subscriber implementation, or a bug, " +
            "please open an issue, see: https://monix.io"
        ))
      // $COVERAGE-ON$
    }

    private final class ChildSubscriber(out: Subscriber[R], asyncUpstreamAck: Promise[Ack])
      extends Subscriber[R] {

      implicit val scheduler = out.scheduler
      private[this] var ack: Future[Ack] = Continue

      // Reusable reference to stop creating function references for each `onNext`
      private[this] val onStopOrFailureRef = (err: Option[Throwable]) => {
        if (err.isDefined) out.scheduler.reportFailure(err.get)
        signalChildOnComplete(Stop, isStop = true)
      }

      def onNext(elem: R) = {
        self.currentState = elem
        ack = out.onNext(elem)
        ack.syncOnStopOrFailure(onStopOrFailureRef)
      }

      def onComplete(): Unit =
        signalChildOnComplete(ack, isStop = false)

      def onError(ex: Throwable): Unit =
        if (!delayErrors) signalChildOnError(ex) else {
          errors.transform(list => ex :: list)
          onComplete()
        }

      private def signalChildOnError(ex: Throwable): Unit = {
        // The cancelable passed in WaitComplete here can be `null`
        // because it would only replace the child's own cancelable
        stateRef.getAndSet(WaitComplete(Some(ex), null)) match {
          case WaitOnActiveChild | WaitOnNextChild(_) | Active(_) =>
            // Branch happens while the main subscriber is still
            // active; the `getAndSet(WaitComplete)` however will
            // stop it and we are free to send the final error
            out.onError(ex)
            asyncUpstreamAck.trySuccess(Stop)

          case WaitComplete(otherEx, _) =>
            // Branch happens when the main subscriber has already
            // finished - we were in `Active` until now, so it is
            // the child's responsibility to finish! But if an
            // exception also happened on main subscriber, we need
            // to log it somewhere!
            otherEx.foreach(scheduler.reportFailure)
            // Send our immediate error downstream and stop everything
            out.onError(ex)
            asyncUpstreamAck.trySuccess(Stop)

          case Cancelled =>
            // User cancelled, but we have to log errors somewhere
            scheduler.reportFailure(ex)
        }
      }

      private def signalChildOnComplete(ack: Future[Ack], isStop: Boolean): Unit = {
        // This assignment must happen after `onNext`, otherwise
        // we can end with a race condition with `onComplete`
        stateRef.getAndSet(WaitOnNextChild(ack)) match {
          case WaitOnActiveChild =>
            () // Optimization, do nothing else

          case WaitOnNextChild(_) | Active(_) =>
            // Branch happens when the main subscriber is still
            // active and this child is thus giving it permission
            // to continue with the next child observable
            ack.value match {
              case Some(result) =>
                asyncUpstreamAck.tryComplete(result)
              case None =>
                asyncUpstreamAck.tryCompleteWith(ack)
            }

          case Cancelled =>
            asyncUpstreamAck.trySuccess(Stop)

          case WaitComplete(exOpt, _) =>
            // An `onComplete` or `onError` event happened since
            // `onNext` was called, so we are now responsible for
            // signaling it downstream.  Note that we've set
            // `WaitOnNext` above, which would make one wonder if
            // we couldn't have a problem with the logic in
            // `onComplete` or `onError`, but if we are seeing
            // this state, it means that these calls already
            // happened, so we can't have a race condition.
            if (!isStop) exOpt match {
              case None => sendOnComplete()
              case Some(ex) => out.onError(ex)
            }
            else ack.value match {
              case Some(Failure(ex)) =>
                // An error happened and we need to report it somewhere
                scheduler.reportFailure(ex)
              case _ =>
                () // do nothing else
            }
        }
      }
    }
  }
}

