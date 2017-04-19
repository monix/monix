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

package monix.reactive.internal.operators

import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{Observable, OverflowStrategy}
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.Future

private[reactive] final
class ObserveOnObservable[+A](source: Observable[A], altS: Scheduler, os: OverflowStrategy[A])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    // Wrapping our `Subscriber` into a `BufferedSubscriber` that uses
    // the provided `Scheduler` in `altS` has the effect that events
    // will be listened on that specified `Scheduler`
    val buffer = {
      val ref: Subscriber[A] = new Subscriber[A] {
        implicit val scheduler = altS
        def onNext(a: A) = out.onNext(a)
        def onError(ex: Throwable) = out.onError(ex)
        def onComplete() = out.onComplete()
      }

      BufferedSubscriber(ref, os)
    }

    // Unfortunately we have to do double wrapping of our Subscriber,
    // because we only want to listen to events on the specified Scheduler,
    // but we don't want to override the Observable's default Scheduler
    source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler =
        out.scheduler
      def onNext(elem: A): Future[Ack] =
        buffer.onNext(elem)
      def onError(ex: Throwable): Unit =
        buffer.onError(ex)
      def onComplete(): Unit =
        buffer.onComplete()
    })
  }
}
