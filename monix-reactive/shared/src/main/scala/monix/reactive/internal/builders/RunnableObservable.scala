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

/** An observable that evaluates the given by-name argument,
  * and emits it.
  */
private[reactive] final class RunnableObservable(r: Runnable)
  extends Observable[Unit] {

  def unsafeSubscribeFn(subscriber: Subscriber[Unit]): Cancelable = {
    try {
      subscriber.onNext(r.run())
      // No need to do back-pressure
      subscriber.onComplete()
    } catch {
      case NonFatal(ex) =>
        try subscriber.onError(ex) catch {
          case NonFatal(err) =>
            val s = subscriber.scheduler
            s.reportFailure(ex)
            s.reportFailure(err)
        }
    }

    Cancelable.empty
  }
}