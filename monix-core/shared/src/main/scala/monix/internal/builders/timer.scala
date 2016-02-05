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

package monix.internal.builders

import java.util.concurrent.TimeUnit
import monix.{Observable, Ack}
import monix.Ack.Continue
import scala.concurrent.duration._
import scala.util.{Failure, Try}

private[monix] object timer {
  /**
    * Create an Observable that repeatedly emits the given `item`, until
    * the underlying Observer cancels.
    */
  def repeated[T](initialDelay: FiniteDuration, period: FiniteDuration, unit: T): Observable[T] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      // we are deploying optimizations in order to reduce garbage allocations,
      // therefore the weird Runnable instance that does a state machine
      val runnable = new Runnable { self =>
        private[this] val periodMs = period.toMillis
        private[this] var startedAt = 0L

        def scheduleNext(r: Try[Ack]): Unit = r match {
          case Continue.AsSuccess =>
            val initialDelay = {
              val duration = s.currentTimeMillis() - startedAt
              val d = periodMs - duration
              if (d >= 0L) d else 0L
            }

            s.scheduleOnce(initialDelay, TimeUnit.MILLISECONDS, self)

          case Failure(ex) =>
            s.reportFailure(ex)

          case _ =>
            () // do nothing
        }

        def run(): Unit = {
          startedAt = s.currentTimeMillis()
          subscriber.onNext(unit).onComplete(scheduleNext)
        }
      }

      s.scheduleOnce(initialDelay.length, initialDelay.unit, runnable)
    }
  }
}
