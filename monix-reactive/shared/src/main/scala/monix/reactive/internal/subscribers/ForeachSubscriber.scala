/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.reactive.internal.subscribers

import monix.eval.Callback
import monix.execution.Ack.{Continue, Stop}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber

/** Subscriber implementation for `Observable.foreach` */
private[reactive] final class ForeachSubscriber[A](
  f: A => Unit,
  onFinish: Callback[Unit],
  s: Scheduler)
  extends Subscriber.Sync[A] {

  implicit val scheduler: Scheduler = s
  private[this] var isDone = false

  def onNext(elem: A): Ack = {
    try {
      f(elem)
      Continue
    } catch {
      case NonFatal(ex) =>
        onError(ex)
        Stop
    }
  }

  def onError(ex: Throwable): Unit =
    if (!isDone) { isDone = true; onFinish.onError(ex) }
  def onComplete(): Unit =
    if (!isDone) { isDone = true; onFinish.onSuccess(()) }
}
