/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import java.util.concurrent.ConcurrentLinkedQueue

import monix.eval.Task
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.CompositeCancelable
import monix.execution.AsyncSemaphore
import monix.execution.ChannelType.MultiProducer
import monix.execution.{ Ack, Cancelable, CancelableFuture }
import monix.reactive.observers.{ BufferedSubscriber, Subscriber }
import monix.reactive.{ Observable, OverflowStrategy }

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

private[reactive] final class MapParallelOrderedObservable[A, B](
  source: Observable[A],
  parallelism: Int,
  f: A => Task[B],
  overflowStrategy: OverflowStrategy[B]
) extends Observable[B] {

  override def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    if (parallelism <= 0) {
      out.onError(new IllegalArgumentException("parallelism > 0"))
      Cancelable.empty
    } else if (parallelism == 1) {
      // optimization for one worker
      new MapTaskObservable[A, B](source, f).unsafeSubscribeFn(out)
    } else {
      val composite = CompositeCancelable()
      val subscription = new MapAsyncParallelSubscription(out, composite)
      composite += source.unsafeSubscribeFn(subscription)
      subscription
    }
  }

  private final class MapAsyncParallelSubscription(out: Subscriber[B], composite: CompositeCancelable)
    extends Subscriber[A] with Cancelable { self =>

    implicit val scheduler = out.scheduler
    // Ensures we don't execute more than a maximum number of tasks in parallel
    private[this] val semaphore = AsyncSemaphore(parallelism.toLong)
    // Buffer with the supplied  overflow strategy.
    private[this] val buffer = BufferedSubscriber[B](out, overflowStrategy, MultiProducer)

    // Flag indicating whether a final event was called, after which
    // nothing else can happen. It's a very light protection, as
    // access to it is concurrent and not synchronized
    private[this] var isDone = false
    // Turns to `Stop` when a stop acknowledgement is observed
    // coming from the `buffer` - this indicates that the downstream
    // no longer wants any events, so we must cancel
    private[this] var lastAck: Ack = Continue
    // Buffer for signaling new elements downstream preserving original order
    // It needs to be thread safe Queue because we want to allow adding and removing
    // elements at the same time.
    private[this] val queue = new ConcurrentLinkedQueue[CancelableFuture[B]]
    // This lock makes sure that only one thread at the time sends processed elements downstream
    private[this] val sendDownstreamSemaphore = AsyncSemaphore(1)

    private def shouldStop: Boolean = isDone || lastAck == Stop

    private def sendDownstreamOrdered(): Unit = {
      // Only one thread should poll queue for completed tasks
      val permit = sendDownstreamSemaphore.acquire()
      composite += permit
      def doSend(): Unit =
        try {
          composite -= permit
          // Keep checking the head of a queue since we have to signal elements in order
          while (!shouldStop && !queue.isEmpty && queue.peek().isCompleted) {
            val head = queue.poll()
            head.value match {
              case Some(Success(value)) =>
                buffer.onNext(value).syncOnComplete {
                  case Success(Stop) =>
                    lastAck = Stop
                    composite.cancel()
                  case Success(Continue) =>
                    semaphore.release()
                    composite -= head.cancelable
                    ()
                  case Failure(ex) =>
                    lastAck = Stop
                    self.onError(ex)
                }

              case Some(Failure(error)) =>
                lastAck = Stop
                composite -= head.cancelable
                self.onError(error)

              case None => // shouldn't get here, we already checked for completion
            }
          }
        } finally {
          sendDownstreamSemaphore.release()
        }
      permit.value match {
        case Some(Success(_)) =>
          doSend()
        case Some(Failure(error)) =>
          lastAck = Stop
          composite -= permit
          self.onError(error)
        case None =>
          permit.onComplete {
            case Success(_) =>
              doSend()
            case Failure(error) =>
              lastAck = Stop
              composite -= permit
              self.onError(error)
          }
      }
    }

    private def process(elem: A) = {
      // For protecting against user code, without violating the
      // observer's contract, by marking the boundary after which
      // we can no longer stream errors downstream
      var streamErrors = true
      try {
        val task = f(elem)
        // No longer allowed to stream errors downstream
        streamErrors = false
        // Start execution (forcing an async boundary)
        val future = task.executeAsync.runToFuture
        composite += future.cancelable
        queue.offer(future)
        future.onComplete {
          case Success(_) =>
            // Current task finished, we can check if there is
            // something to send to the downstream subscriber
            sendDownstreamOrdered()

          case Failure(error) =>
            lastAck = Stop
            composite -= future.cancelable
            composite.cancel()
            self.onError(error)
        }
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors) self.onError(ex)
          else scheduler.reportFailure(ex)
      }
    }

    def onNext(elem: A): Future[Ack] = {
      // Light protection, since access isn't synchronized
      if (shouldStop) Stop
      else {
        // This will wait asynchronously, if there are no permits left
        val permit = semaphore.acquire()
        composite += permit

        val ack: Future[Ack] = permit.value match {
          case None =>
            permit.flatMap { _ =>
              composite -= permit
              process(elem)
              Continue
            }
          case Some(_) =>
            composite -= permit
            process(elem)
            Continue
        }

        ack.onComplete {
          case Failure(ex) =>
            // Take out the garbage
            composite -= permit
            self.onError(ex)
          case _ =>
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
        queue.clear()
        // Outsourcing the handling and safety of onError
        // to our concurrent buffer implementation
        buffer.onError(ex)
      }
    }

    def onComplete(): Unit = {
      // We need to wait for all semaphore permits to be
      // released, otherwise we can lose events and that's
      // not acceptable for onComplete!
      semaphore.awaitAvailable(parallelism.toLong).foreach { _ =>
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
