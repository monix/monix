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

import java.util.concurrent.TimeUnit
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}

private[reactive] final class BufferTimedObservable[+A](
  source: Observable[A],
  timespan: FiniteDuration,
  maxCount: Int)
  extends Observable[Seq[A]] {

  require(timespan > Duration.Zero, "timespan must be strictly positive")
  require(maxCount >= 0, "maxCount must be positive")

  def unsafeSubscribeFn(out: Subscriber[Seq[A]]): Cancelable = {
    val periodicTask = MultiAssignmentCancelable()

    val connection = source.unsafeSubscribeFn(
      new Subscriber[A] with Runnable { self =>
        implicit val scheduler = out.scheduler

        private[this] val timespanMillis = timespan.toMillis
        // MUST BE synchronized by `self`
        private[this] var ack: Future[Ack] = Continue
        // MUST BE synchronized by `self`
        private[this] var buffer = ListBuffer.empty[A]
        // MUST BE synchronized by `self`
        private[this] var expiresAt = scheduler.currentTimeMillis() + timespanMillis

        locally {
          // Scheduling the first tick, in the constructor
          periodicTask := out.scheduler.scheduleOnce(timespanMillis, TimeUnit.MILLISECONDS, self)
        }

        // Runs periodically, every `timespan`
        def run(): Unit = self.synchronized {
          val now = scheduler.currentTimeMillis()
          // Do we still have time remaining?
          if (now < expiresAt) {
            // If we still have time remaining, it's either a scheduler
            // problem, or we rushed to signaling the bundle upon reaching
            // the maximum size in onNext. So we sleep some more.
            val remaining = expiresAt - now
            periodicTask := scheduler.scheduleOnce(remaining, TimeUnit.MILLISECONDS, self)
          }
          else if (buffer != null) {
            // The timespan has passed since the last signal so we need
            // to send the current bundle
            sendNextAndReset(now).syncOnContinue(
              // Schedule the next tick, but only after we are done
              // sending the bundle
              run())
          }
        }

        // Must be synchronized by `self`
        private def sendNextAndReset(now: Long): Future[Ack] = {
          val oldBuffer = buffer.toList
          // Reset
          buffer = ListBuffer.empty[A]
          // Setting the time of the next scheduled tick
          expiresAt = now + timespanMillis
          ack = ack.syncTryFlatten.syncFlatMap {
            case Continue => out.onNext(oldBuffer)
            case Stop => Stop
          }
          ack
        }

        def onNext(elem: A): Future[Ack] = self.synchronized {
          val now = scheduler.currentTimeMillis()
          buffer.append(elem)

          if (expiresAt <= now || (maxCount > 0 && maxCount <= buffer.length))
            sendNextAndReset(now)
          else
            Continue
        }

        def onError(ex: Throwable): Unit = self.synchronized {
          periodicTask.cancel()
          ack = Stop
          buffer = null
          out.onError(ex)
        }

        def onComplete(): Unit = self.synchronized {
          periodicTask.cancel()

          if (buffer.nonEmpty) {
            val bundleToSend = buffer.toList
            // In case the last onNext isn't finished, then
            // we need to apply back-pressure, otherwise this
            // onNext will break the contract.
            ack.syncOnContinue {
              out.onNext(bundleToSend)
              out.onComplete()
            }
          } else {
            // We can just stream directly
            out.onComplete()
          }

          // GC relief
          buffer = null
          // Ensuring that nothing else happens
          ack = Stop
        }
      })

    CompositeCancelable(connection, periodicTask)
  }
}