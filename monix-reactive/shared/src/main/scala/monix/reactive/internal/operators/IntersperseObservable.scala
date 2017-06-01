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

import monix.execution.Ack.Continue
import monix.execution.atomic.AtomicBoolean
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final class IntersperseObservable[+A](source: Observable[A],
                                                        separator: A) extends Observable[A]{ self =>

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val firstTime = AtomicBoolean(true)
    val isDone = AtomicBoolean(false)
    var downstreamAck = Continue : Future[Ack]

    val upstream = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      override def onNext(elem: A): Future[Ack] = {
        downstreamAck = if (firstTime.getAndSet(false)) {
          out.onNext(elem)
        }
        else {
          out.onNext(separator).flatMap {
            case Continue => out.onNext(elem)
            case ack => ack
          }
        }
        downstreamAck
      }

      def onError(ex: Throwable) = {
        if (!isDone.getAndSet(true)){
          out.onError(ex)
        }
      }
      def onComplete() = {
        downstreamAck.syncOnContinue {
          if (!isDone.getAndSet(true)){
            out.onComplete()
          }
        }
      }
    })

    Cancelable { () =>
      upstream.cancel()
    }
  }
}
