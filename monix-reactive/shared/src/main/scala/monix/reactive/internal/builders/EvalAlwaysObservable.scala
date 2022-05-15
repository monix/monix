/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.reactive.internal.builders

import monix.execution.Cancelable
import scala.util.control.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

/** An observable that evaluates the given by-name argument,
  * and emits it.
  */
private[reactive] final class EvalAlwaysObservable[+A](f: () => A) extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    try {
      subscriber.onNext(f())
      // No need to do back-pressure
      subscriber.onComplete()
    } catch {
      case ex if NonFatal(ex) =>
        try subscriber.onError(ex)
        catch {
          case err if NonFatal(err) =>
            val s = subscriber.scheduler
            s.reportFailure(ex)
            s.reportFailure(err)
        }
    }

    Cancelable.empty
  }
}
