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

package monix.internal.operators

import monix.Ack.{Cancel, Continue}
import monix.{Observer, Observable}
import scala.concurrent.Future
import scala.util.control.NonFatal

private[monix] object collect {
  /**
   * Implementation for [[Observable.collect]].
   */
  def apply[T,U](source: Observable[T])(pf: PartialFunction[T,U]): Observable[U] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            if (pf.isDefinedAt(elem)) {
              val next = pf(elem)
              streamError = false
              subscriber.onNext(next)
            }
            else
              Continue
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { subscriber.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onError(ex: Throwable) =
          subscriber.onError(ex)

        def onComplete() =
          subscriber.onComplete()
      })
    }
  }
}
