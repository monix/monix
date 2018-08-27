/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import monix.reactive.{Observable, OverflowStrategy}

/** Implementation for [[monix.reactive.Observable.create]]. */
private[reactive] final class CreateObservable[+A](
  overflowStrategy: OverflowStrategy.Synchronous[A],
  f: Subscriber.Sync[A] => Cancelable)
  extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val out = BufferedSubscriber.synchronous(subscriber, overflowStrategy)
    try f(out) catch {
      case ex if NonFatal(ex) =>
        subscriber.scheduler.reportFailure(ex)
        Cancelable.empty
    }
  }
}
