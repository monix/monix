/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.builders

import java.util.concurrent.TimeUnit
import monifu.reactive.Observable
import monifu.reactive.internals._
import scala.concurrent.duration._


private[reactive] object interval {
  /**
   * Implementation for [[Observable.intervalWithFixedDelay]].
   */
  def withFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration): Observable[Long] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      s.scheduleOnce(initialDelay, new Runnable { self =>
        import monifu.reactive.internals.ObserverState.{ON_CONTINUE, ON_NEXT}
        var state = ON_NEXT
        var counter = 0L

        def run() = state match {
          case ON_NEXT =>
            state = ON_CONTINUE
            o.onNext(counter).onContinue(self)

          case ON_CONTINUE =>
            state = ON_NEXT
            counter += 1
            s.scheduleOnce(delay, self)
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.intervalAtFixedRate]].
   */
  def atFixedRate(initialDelay: FiniteDuration, period: FiniteDuration): Observable[Long] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      s.scheduleOnce(initialDelay, new Runnable { self =>
        import monifu.reactive.internals.ObserverState.{ON_CONTINUE, ON_NEXT}

        private[this] val periodMillis = period.toMillis
        private[this] var state = ON_NEXT
        private[this] var counter = 0L
        private[this] var startedAt = 0L

        def run() = state match {
          case ON_NEXT =>
            state = ON_CONTINUE
            startedAt = s.currentTimeMillis()
            o.onNext(counter).onContinue(self)

          case ON_CONTINUE =>
            state = ON_NEXT
            counter += 1

            val delay = {
              val durationMillis = s.currentTimeMillis() - startedAt
              val d = periodMillis - durationMillis
              if (d >= 0L) d else 0L
            }

            s.scheduleOnce(delay, TimeUnit.MILLISECONDS, self)
        }
      })
    }
  }
}
