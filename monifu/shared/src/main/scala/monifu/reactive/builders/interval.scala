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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.concurrent.{Scheduler, Cancelable}
import monifu.reactive.internals._
import monifu.reactive.{Observable, Observer}
import scala.concurrent.duration._

object interval {
  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced equally by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param delay the delay between two subsequent events
   */
  def withFixedDelay(delay: FiniteDuration): Observable[Long] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      var counter = 0
      s.scheduleRecursive(Duration.Zero, delay, { reschedule =>
        o.onNext(counter).onContinue {
          counter += 1
          reschedule()
        }
      })
    }
  }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) emitted
   * at a fixed rate, as specified by `period`.
   *
   * @param period the delay between two subsequent events
   */
  def atFixedRate(period: FiniteDuration): Observable[Long] = {
    /*
     * Helper that recursively loops, feeding the given observer with an incremented
     * counter at a fixed rate.
     */
    def loop(o: Observer[Long], counter: Long, initialDelay: FiniteDuration, period: FiniteDuration)(implicit s: Scheduler): Cancelable =
      s.scheduleOnce(initialDelay, {
        val startedAt = s.nanoTime

        o.onNext(counter).onContinue {
          val duration = (s.nanoTime - startedAt).nanos
          val delay = {
            val d = period - duration
            if (d >= Duration.Zero) d else Duration.Zero
          }

          loop(o, counter + 1, delay, period)
        }
      })

    Observable.create { s =>
      loop(s.observer, 0, Duration.Zero, period)(s.scheduler)
    }
  }
}
