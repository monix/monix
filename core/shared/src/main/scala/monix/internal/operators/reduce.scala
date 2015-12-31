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
import monix.{Ack, Observer, Observable}
import monix.internal._
import scala.concurrent.Future
import scala.util.control.NonFatal

private[monix] object reduce {
  /**
   * Implementation for [[monix.Observable.reduce]].
   */
  def apply[T](source: Observable[T])(op: (T, T) => T): Observable[T] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var state: T = _
        private[this] var isFirst = true
        private[this] var wasApplied = false

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            if (isFirst) {
              isFirst = false
              state = elem
            }
            else {
              state = op(state, elem)
              if (!wasApplied) wasApplied = true
            }

            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onComplete() = {
          if (wasApplied) {
            subscriber.onNext(state)
              .onContinueSignalComplete(subscriber)
          }
          else {
            subscriber.onComplete()
          }
        }

        def onError(ex: Throwable) = {
          subscriber.onError(ex)
        }
      })
    }
  }
}
