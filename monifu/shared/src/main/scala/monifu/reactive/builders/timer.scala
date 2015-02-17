/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.reactive.Observable
import monifu.reactive.internals._
import scala.concurrent.duration._

object timer {
  /**
   * Create an Observable that repeatedly emits the given `item`, until
   * the underlying Observer cancels.
   */
  def repeated[T](initialDelay: FiniteDuration, period: FiniteDuration, unit: T): Observable[T] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      // we are deploying optimizations in order to reduce garbage allocations,
      // therefore the weird Runnable instance that does a state machine
      val runnable = new Runnable { self =>
        import ObserverState.{ON_CONTINUE, ON_NEXT}
        var state = ON_NEXT
        var startedAt = 0L

        def run(): Unit = state match {
          case ON_NEXT =>
            state = ON_CONTINUE
            startedAt = s.nanoTime()
            observer.onNext(unit).onContinue(self)

          case ON_CONTINUE =>
            state = ON_NEXT
            val initialDelay = {
              val duration = (s.nanoTime() - startedAt).nanos
              val d = period - duration
              if (d >= Duration.Zero) d else Duration.Zero
            }

            s.scheduleOnce(initialDelay, self)
        }
      }

      s.scheduleOnce(initialDelay, runnable)
    }
  }
}
