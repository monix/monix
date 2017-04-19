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

package monix.reactive.internal.builders

import monix.execution.Cancelable
import monix.execution.misc.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

private[reactive] final class DeferObservable[+A](factory: => Observable[A])
  extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    // Protect against user code, but if the subscription fails
    // then the behavior should be left undefined, otherwise we
    // can get weird effects.
    var streamErrors = true
    try {
      val source = factory
      streamErrors = false
      source.unsafeSubscribeFn(subscriber)
    } catch {
      case NonFatal(ex) if streamErrors =>
        subscriber.onError(ex)
        Cancelable.empty
    }
  }
}
