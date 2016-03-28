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

package monifu.reactive.internals.builders

import monifu.reactive.Observable
import monifu.reactive.internals._
import scala.concurrent.duration.FiniteDuration


private[reactive] object unit {
  /**
   * Implementation for [[Observable.unit]].
   */
  def one[A](elem: A): Observable[A] =
    Observable.create { s =>
      s.onNext(elem)
        .onContinueSignalComplete(s)(s.scheduler)
    }

  /**
   * Implementation for [[Observable.unitDelayed]].
   */
  def oneDelayed[A](delay: FiniteDuration, elem: A): Observable[A] =
    Observable.create { s =>
      s.scheduler.scheduleOnce(delay) {
        s.onNext(elem)
          .onContinueSignalComplete(s)(s.scheduler)
      }
    }

  /**
   * Implementation for [[Observable.empty]].
   */
  def empty: Observable[Nothing] =
    Observable.create(_.onComplete())

  /**
   * Implementation for [[Observable.error]].
   */
  def error(ex: Throwable): Observable[Nothing] =
    Observable.create(_.onError(ex))

  /**
   * Implementation for [[Observable.never]].
   */
  def never: Observable[Nothing] =
    Observable.create { _ => () }
}
