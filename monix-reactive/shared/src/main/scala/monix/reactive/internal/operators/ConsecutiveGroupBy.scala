/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.PublishSubject

import scala.concurrent.Future

final class ConsecutiveGroupBy[A](includeInGroup: (A, A) => Boolean) extends Operator[A, Observable[A]] {

  def apply(out: Subscriber[Observable[A]]): Subscriber[A] =
    new Subscriber[A] {
      override implicit val scheduler: Scheduler = out.scheduler
      private[this] var isDone = false
      private[this] var subStream: PublishSubject[A] = PublishSubject[A]()
      private[this] var previous: Option[A] = _

      override def onNext(elem: A): Future[Ack] = {
        if (isDone) Stop
        else {
          if (previous.exists(includeInGroup(_, elem))) {
            previous = Some(elem)
            subStream.onNext(elem)
          } else {
            previous = Some(elem)
            if (previous.isDefined) {
              subStream.onComplete()
              subStream = PublishSubject[A]()
            }
            out.onNext(subStream).flatMap {
              case Ack.Continue => subStream.onNext(elem)
              case Ack.Stop => Stop
            }
          }
        }
      }

      override def onComplete(): Unit =
        if (isDone) {
          isDone = true
          subStream.onComplete()
          out.onComplete()
        }

      override def onError(ex: Throwable): Unit =
        if (isDone) {
          isDone = true
          subStream.onError(ex)
          out.onError(ex)
        }
    }

}
