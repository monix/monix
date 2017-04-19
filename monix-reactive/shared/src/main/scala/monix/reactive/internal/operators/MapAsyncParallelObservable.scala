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
import monix.eval.{Callback, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.internal.Platform
import monix.execution.misc.{AsyncSemaphore, NonFatal}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.BackPressure
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Implementation for a `mapAsync` operator that can execute multiple tasks
  * in parallel. Similar with
  * [[monix.reactive.Consumer.loadBalance(parallelism* Consumer.loadBalance]],
  * but expressed as an operator.
  *
  * Implementation considerations:
  *
  *  - given we've got concurrency in processing `onNext` events, we need
  *    to use a concurrent buffer to guarantee safety
  *  - to ensure the required parallelism factor, we are using an
  *    [[monix.execution.misc.AsyncSemaphore]]
  */
private[reactive] final class MapAsyncParallelObservable[A,B]
  (source: Observable[A], parallelism: Int, f: A => Task[B])
  extends Observable[B] {

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    if (parallelism <= 0) {
      out.onError(new IllegalArgumentException("parallelism > 0"))
      Cancelable.empty
    }
    else if (parallelism == 1) {
      // optimization for one worker
      new MapTaskObservable[A,B](source, f).unsafeSubscribeFn(out)
    }
    else {
      val composite = CompositeCancelable()
      val subscription = new MapAsyncParallelSubscription(out, composite)
      composite += source.unsafeSubscribeFn(subscription)
      subscription
    }
  }

  private final class MapAsyncParallelSubscription(
    out: Subscriber[B], composite: CompositeCancelable)
    extends Subscriber[A] with Cancelable { self =>

    implicit val scheduler = out.scheduler
    // Ensures we don't execute more then a maximum number of tasks in parallel
    private[this] val semaphore = AsyncSemaphore(parallelism)
    // Reusable instance for releasing permits on cancel, but
    // it's debatable whether this is needed, since on cancel
    // everything gets canceled at once
    private[this] val releaseTask = Task.eval(semaphore.release())
    // Concurrent buffer implementation with the back-pressure
    // used as an overflow strategy, meaning that when the buffer
    // is full then the data source gets frozen
    private[this] val buffer = {
      val strategy = BackPressure(parallelism + Platform.recommendedBatchSize)
      BufferedSubscriber[B](out, strategy)
    }

    // Flag indicating whether a final event was called, after which
    // nothing else can happen. It's a very light protection, as
    // access to it is concurrent and not synchronized
    private[this] var isDone = false
    // Turns to `Stop` when a stop acknowledgement is observed
    // coming from the `buffer` - this indicates that the downstream
    // no longer wants any events, so we must cancel
    private[this] var lastAck: Ack = Continue

    private def process(elem: A) = {
      // For protecting against user code, without violating the
      // observer's contract, by marking the boundary after which
      // we can no longer stream errors downstream
      var streamErrors = true
      try {
        // We need a forward reference, because of the
        // interaction with the `composite` below
        val subscription = SingleAssignmentCancelable()
        composite += subscription

        val task = {
          val ref = f(elem).materializeAttempt.map {
            case Now(value) =>
              buffer.onNext(value).syncOnComplete {
                case Success(Stop) =>
                  lastAck = Stop
                  composite.cancel()
                case Success(Continue) =>
                  semaphore.release()
                  composite -= subscription
                case Failure(ex) =>
                  lastAck = Stop
                  composite -= subscription
                  self.onError(ex)
              }
            case Error(ex) =>
              lastAck = Stop
              composite -= subscription
              self.onError(ex)
          }

          ref.doOnCancel(releaseTask)
        }

        // No longer allowed to stream errors downstream
        streamErrors = false
        // Start execution
        subscription := task.runAsync(Callback.empty)
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) self.onError(ex)
          else scheduler.reportFailure(ex)
      }
    }

    def onNext(elem: A): Future[Ack] = {
      // Light protection, since access isn't synchronized
      if (lastAck == Stop || isDone) Stop else {
        // This will wait asynchronously, if there are no permits left
        val permit = semaphore.acquire()
        val ack: Future[Ack] = permit.value match {
          case None => permit.flatMap(_ => Continue)
          case Some(_) => Continue
        }

        composite += permit
        ack.onComplete {
          case Success(_) =>
            // Take out the garbage
            composite -= permit
            // Actual processing and sending, however we are not
            // applying back-pressure here. The back-pressure applied
            // is solely on `semaphore.acquire()` and that's it.
            // Indirectly it will also back-pressure on the buffer when
            // full, but that happens because of how we `release()`
            process(elem)

          case Failure(ex) =>
            // Take out the garbage
            composite -= permit
            self.onError(ex)
        }

        // As noted already, the back-pressure happening here
        // is solely on `semaphore.acquire()`, see above
        ack.syncTryFlatten
      }
    }

    def onError(ex: Throwable): Unit = {
      if (!isDone) {
        isDone = true
        lastAck = Stop
        // Outsourcing the handling and safety of onError
        // to our concurrent buffer implementation
        buffer.onError(ex)
      }
    }

    def onComplete(): Unit = {
      // We need to wait for all semaphore permits to be
      // released, otherwise we can lose events and that's
      // not acceptable for onComplete!
      semaphore.awaitAllReleased().foreach { _ =>
        if (!isDone) {
          isDone = true
          lastAck = Stop
          // Outsourcing the handling and safety of onComplete
          // to our concurrent buffer implementation
          buffer.onComplete()
        }
      }
    }

    def cancel(): Unit = {
      // We are canceling permits as well, so this is necessary to prevent
      // `onComplete` / `onError` signals from main subscriber
      isDone = true
      composite.cancel()
    }
  }
}