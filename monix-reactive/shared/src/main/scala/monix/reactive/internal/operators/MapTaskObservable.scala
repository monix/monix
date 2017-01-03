/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import monix.eval.Coeval.{Error, Now}
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal

/** Implementation for `Observable.mapTask`.
  *
  * Example of what we want to achieve:
  * {{{
  *   observable.mapTask(x => Task(x + 1))
  * }}}
  *
  * This is basically equivalent with:
  * {{{
  *   observable.concatMap(x => Observable.fromTask(Task(x + 1)))
  * }}}
  *
  * The implementation has to be faster than `concatMap`.
  * The challenges are:
  *
  *  - keeping track of the `Cancelable` for the active task
  *
  *  - dealing with the concurrency between the last `onNext` and the
  *    final `onComplete` or `onError`, because with the `Observer`
  *    contract we are allowed to send a final event after the last
  *    `onNext` is called, but before its `Future[Ack]` is finished
  *
  * This implementation is fast because:
  *
  *  - the state is stored and synchronized using an atomic reference
  *    that is padded to avoid false sharing
  *
  *  - in order to evolve the state we are NOT using intrinsic
  *    monitors (`synchronize`), or `compareAndSet`, but plain writes,
  *    along with `getAndSet`
  *
  *  - currently for each `onNext` we are doing 2 `getAndSet`
  *    operations per `onNext` call, which is awesome because on top
  *    of Java 8 `getAndSet` operations are cheaper than
  *    `compareAndSet`. It is more of a problem on Java 7 and below,
  *    however it's OK-ish, since these CAS operations are not going
  *    to be contended
  */
private[reactive] final class MapTaskObservable[A,B]
  (source: Observable[A], f: A => Task[B])
  extends Observable[B] {

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    val subscriber = new MapAsyncSubscriber(out)
    val mainSubscription = source.unsafeSubscribeFn(subscriber)

    Cancelable { () =>
      try mainSubscription.cancel()
      finally subscriber.cancel()
    }
  }

  private final class MapAsyncSubscriber(out: Subscriber[B])
    extends Subscriber[A] with Cancelable { self =>

    import MapTaskObservable.FlatMapState
    import MapTaskObservable.FlatMapState._

    implicit val scheduler = out.scheduler
    // For synchronizing our internal state machine, padded
    // in order to avoid the false sharing problem
    private[this] val stateRef =
      Atomic.withPadding(WaitOnNext : FlatMapState, LeftRight128)

    /** For canceling the current active task, in case there is any. Here
      * we can afford a `compareAndSet`, not being a big deal since
      * cancellation only happens once.
      */
    @tailrec def cancel(): Unit =
      stateRef.get match {
        case current @ Active(ref) =>
          if (stateRef.compareAndSet(current, Cancelled))
            ref.cancel()
          else
            cancel() // retry
        case current @ (WaitOnNext | WaitActiveTask) =>
          if (!stateRef.compareAndSet(current, Cancelled))
            cancel() // retry
        case WaitComplete(_) | Cancelled =>
          () // do nothing else
      }

    def onNext(elem: A): Future[Ack] = {
      // For protecting against user code, without violating the
      // observer's contract, by marking the boundary after which
      // we can no longer stream errors downstream
      var streamErrors = true
      try {
        val task = f(elem).materializeAttempt.flatMap {
          case Now(value) =>
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

              case WaitComplete(exOpt) =>
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

          case Error(ex) =>
            stateRef.getAndSet(WaitComplete(Some(ex))) match {
              case WaitActiveTask | WaitOnNext | Active(_) =>
                Task.eval {
                  out.onError(ex)
                  Stop
                }

              case WaitComplete(otherEx) =>
                // If an exception also happened on main observable, log it!
                otherEx.foreach(scheduler.reportFailure)
                // Send our immediate error downstream and stop everything
                out.onError(ex)
                Task.now(Stop)

              case Cancelled =>
                // User cancelled, but we have to log it somewhere
                scheduler.reportFailure(ex)
                Task.now(Stop)
            }
        }

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
          case WaitActiveTask =>
            ack // expected outcome

          case WaitOnNext =>
            // Task execution was synchronous, w00t, so redo state!
            stateRef.lazySet(WaitOnNext)
            ack.syncTryFlatten

          case state @ (WaitComplete(_) | Active(_)) =>
            // These should never, ever happen!
            self.onError(new IllegalStateException(
              s"State $state is invalid, please send a bug report!"))
            Stop

          case Cancelled =>
            // Race condition, oops, now cancel the active task
            ack.cancel()
            // Now restore the state and pretend that this didn't
            // happen :-) Note that this is probably going to call
            // `ack.cancel()` a second time, but that's OK
            self.cancel()
            Stop
        }
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) {
            onError(ex)
            Stop
          } else {
            scheduler.reportFailure(ex)
            Stop
          }
      }
    }

    private def signalFinish(ex: Option[Throwable]): Unit =
      stateRef.getAndSet(WaitComplete(ex)) match {
        case WaitOnNext =>
          // In this state we know we have no active task,
          // and we know that the last `onNext` was triggered
          // (but its future not necessarily finished)
          // so we can just signal the completion event
          if (ex.isEmpty) out.onComplete() else out.onError(ex.get)

        case Active(_) | WaitComplete(_) | Cancelled =>
          // We either have an active task, or this is a duplicate
          // event, hence we cannot signal the completion signal here.
          ()

        case WaitActiveTask =>
          // Something is screwed up in our state machine :-(
          scheduler.reportFailure(
            new IllegalStateException(
              "State WaitActiveTask is invalid, please send a bug report!"
            ))
      }

    def onComplete(): Unit =
      signalFinish(None)
    def onError(ex: Throwable): Unit =
      signalFinish(Some(ex))
  }
}

object MapTaskObservable {
  /** Internal, private state for the [[MapTaskObservable]]
    * implementation, modeling its state machine for managing
    * the active task.
    */
  private sealed abstract class FlatMapState

  private object FlatMapState {
    /** The initial state of our internal atomic in [[MapTaskObservable]].
      *
      * This state is being set in `onNext` and when it is observed it
      * means that no task is currently being executed. If during this
      * state the `onComplete` or `onError` final events are signaled
      * by the source observable, then we are free to signal these
      * events downstream directly. Because otherwise, if we have an
      * active task, it becomes the responsibility of that active task
      * to signal these events.
      */
    case object WaitOnNext extends FlatMapState

    /** A state that when observed it means that an `onNext` call is
      * currently in progress, but its corresponding task wasn't
      * executed yet.
      *
      * This state is meant to detect synchronous execution in
      * `onNext`, and it can never be seen outside `onNext`. This
      * means that if it is observed in `onComplete` or `onError`,
      * then it's a bug.
      */
    case object WaitActiveTask extends FlatMapState

    /** A state that happens after the user cancelled his subscription.
      *
      * Handling of this state is not accurate, meaning that we could
      * have logic being executed and states being evolved after a
      * cancel took place, but if we observe a cancel took place prior
      * to one of our `getAndSet` calls, then we trigger the `cancel`
      * again within a safe compare-and-set loop.
      */
    case object Cancelled extends FlatMapState

    /** This state is triggered by `onComplete` or `onError` while there
      * is an active task being executed.
      *
      * The contract of `Observer` allows for `onComplete` and
      * `onError` events to be sent before the `Future[Ack]` of the
      * last `onNext` call is complete. But at the same time we can
      * never send an `onNext` event after an `onComplete` or an
      * `onError`. So while a task is being processed, but before the
      * `onNext` event is sent, if a complete event happens it becomes
      * the responsibility of the active task (initialized in
      * `onNext`) to send this final event.
      */
    final case class WaitComplete(ex: Option[Throwable]) extends FlatMapState

    /** State that happens after a task has been executed, but before the
      * following `onNext` call on the downstream subscriber.
      *
      * Tasks are cancelable and the reference kept when in this state
      * can be used to cancel it, the final cancelable triggering a
      * cancellation on both the source and the active task when
      * possible.
      */
    final case class Active(ref: Cancelable) extends FlatMapState
  }
}
