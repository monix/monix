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

import monifu.concurrent.Scheduler
import monifu.reactive.Observable
import monifu.reactive.internals._
import scala.concurrent.duration.FiniteDuration


object timer {

  /**
   * Create an Observable that emits a single item after a given delay.
   */
  def oneTime[T](delay: FiniteDuration, unit: T)(implicit s: Scheduler): Observable[T] =
    Observable.create { observer =>
      s.scheduleOnce(delay, {
        observer.onNext(unit)
        observer.onComplete()
      })
    }

  /**
   * Create an Observable that repeatedly emits the given `item`, until
   * the underlying Observer cancels.
   */
  def repeated[T](initialDelay: FiniteDuration, period: FiniteDuration, unit: T)
      (implicit s: Scheduler): Observable[T] = {

    Observable.create { observer =>
      s.scheduleRecursive(initialDelay, period, { reschedule =>
        observer.onNext(unit).onContinue {
          reschedule()
        }
      })
    }
  }
}
