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

package monix.reactive.internal.operators

import monix.execution.Ack.Stop
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class ScanObservable[A,R](
  source: Observable[A], initial: () => R, f: (R,A) => R)
  extends Observable[R] {

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    var streamErrors = true
    try {
      val initialState = initial()
      streamErrors = false

      // Initial state was evaluated, subscribing to source
      source.unsafeSubscribeFn(
        new Subscriber[A] {
          implicit val scheduler = out.scheduler
          private[this] var isDone = false
          private[this] var state = initialState

          def onNext(elem: A): Future[Ack] = {
            // Protects calls to user code from within the operator and
            // stream the error downstream if it happens, but if the
            // error happens because of calls to `onNext` or other
            // protocol calls, then the behavior should be undefined.
            var streamError = true
            try {
              state = f(state, elem)
              streamError = false
              out.onNext(state)
            }
            catch {
              case NonFatal(ex) if streamError =>
                onError(ex)
                Stop
            }
          }

          def onError(ex: Throwable): Unit =
            if (!isDone) {
              isDone = true
              out.onError(ex)
            }

          def onComplete(): Unit =
            if (!isDone) {
              isDone = true
              out.onComplete()
            }
        })
    }
    catch {
      case NonFatal(ex) if streamErrors =>
        // The initial state triggered an error
        out.onError(ex)
        Cancelable.empty
    }
  }
}