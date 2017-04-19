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

import monix.execution.Ack.{Continue, Stop}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.internal.builders.UnsafeCreateObservable.SafeSubscriber
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

/** Implementation for [[monix.reactive.Observable.unsafeCreate]]. */
private[reactive] final class UnsafeCreateObservable[+A](f: Subscriber[A] => Cancelable)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable =
    try f(new SafeSubscriber[A](out)) catch {
      case NonFatal(ex) =>
        out.scheduler.reportFailure(ex)
        Cancelable.empty
    }
}

private[reactive] object UnsafeCreateObservable {
  /** Wraps a subscriber into an implementation that protects the
    * grammar. The light version of [[monix.reactive.observers.SafeSubscriber]].
    */
  private final class SafeSubscriber[-A](underlying: Subscriber[A])
    extends Subscriber[A] { self =>

    implicit val scheduler: Scheduler = underlying.scheduler
    private[this] var isDone = false

    def onNext(elem: A): Future[Ack] =
      if (isDone) Stop else {
        val ack = try underlying.onNext(elem) catch {
          case NonFatal(ex) =>
            self.onError(ex)
            Stop
        }

        if (ack eq Continue)
          Continue
        else if (ack eq Stop) {
          isDone = true
          Stop
        }
        else
          ack
      }

    def onError(ex: Throwable): Unit =
      if (!isDone) {
        isDone = true
        underlying.onError(ex)
      }

    def onComplete(): Unit =
      if (!isDone) {
        isDone = true
        underlying.onComplete()
      }
  }
}
