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

import monix.execution.Cancelable
import monix.execution.misc.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

private[reactive] object DoOnSubscribeObservable {
  // Implementation for doBeforeSubscribe
  final class Before[+A](source: Observable[A], callback: () => Unit) extends Observable[A] {
    def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
      var streamError = true
      try {
        callback()
        streamError = false
        source.unsafeSubscribeFn(subscriber)
      } catch {
        case NonFatal(ex) if streamError =>
          subscriber.onError(ex)
          Cancelable.empty
      }
    }
  }

  // Implementation for doAfterSubscribe
  final class After[+A](source: Observable[A], callback: () => Unit) extends Observable[A] {
    def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
      import subscriber.{scheduler => s}
      val cancelable = source.unsafeSubscribeFn(subscriber)
      try callback() catch { case NonFatal(ex) => s.reportFailure(ex) }
      cancelable
    }
  }
}
