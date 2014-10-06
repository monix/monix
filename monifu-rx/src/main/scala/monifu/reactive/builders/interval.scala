/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.concurrent.{Cancelable, Scheduler}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observable, Observer}

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object interval {
  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced equally by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param delay the delay between two subsequent events
   * @param s the scheduler used for scheduling the periodic signaling of onNext
   */
  def withFixedDelay(delay: FiniteDuration)(implicit s: Scheduler): Observable[Long] = {
    Observable.create { o =>
      var counter = 0

      s.scheduleRecursive(Duration.Zero, delay, { reschedule =>
        try o.onNext(counter).onComplete {
            case Success(Continue) =>
              counter += 1
              reschedule()
            case Success(Cancel) =>
              // do nothing else
            case Failure(ex) =>
              o.onError(ex)
        }
        catch {
          case NonFatal(ex) =>
            o.onError(ex)
        }
      })
    }
  }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) emitted
   * at a fixed rate, as specified by `period`.
   *
   * @param period the delay between two subsequent events
   * @param s the scheduler used for scheduling the periodic signaling of onNext
   */
  def atFixedRate(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] = {
    /**
     * Helper that recursively loops, feeding the given observer with an incremented
     * counter at a fixed rate.
     */
    def loop(o: Observer[Long], counter: Long, initialDelay: FiniteDuration, period: FiniteDuration): Cancelable =
      s.scheduleOnce(initialDelay, {
        val emittedAt = System.currentTimeMillis()

        try o.onNext(counter).onComplete {
          case Success(ack) => ack match {
            case Cancel => // ignore
            case Continue =>
              val durationInMillis = System.currentTimeMillis() - emittedAt
              val delay = scala.math.max(0L, period.toMillis - durationInMillis).millis
              loop(o, counter + 1, delay, period)
          }
          case Failure(ex) =>
            o.onError(ex)
        }
        catch {
          case NonFatal(ex) =>
            o.onError(ex)
        }
      })

    Observable.create { observer =>
      loop(observer, 0, Duration.Zero, period)
    }
  }
}
