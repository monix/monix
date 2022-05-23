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

package monix.reactive.instances

import cats.arrow.Profunctor
import monix.execution.Ack.Stop
import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.Subject

import scala.concurrent.Future
import scala.util.control.NonFatal

/** `cats.arrow.Profunctor` type class instance for [[monix.reactive.subjects.Subject Subject]].
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
object CatsProfunctorForSubject extends Profunctor[Subject] {

  def dimap[A, B, C, D](source: Subject[A, B])(f: C => A)(g: B => D): Subject[C, D] =
    new ProfunctorSubject(source)(f)(g)

  private final class ProfunctorSubject[A, B, C, D](source: Subject[A, B])(f: C => A)(g: B => D) extends Subject[C, D] {

    def size: Int = source.size

    def unsafeSubscribeFn(subscriber: Subscriber[D]): Cancelable =
      source.unsafeSubscribeFn(new Subscriber[B] {

        implicit def scheduler: Scheduler = subscriber.scheduler

        def onNext(elem: B): Future[Ack] = {
          var streamError = true
          try {
            val b = g(elem)
            streamError = false
            subscriber.onNext(b)
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
              Stop
          }
        }

        def onError(ex: Throwable): Unit = subscriber.onError(ex)

        def onComplete(): Unit = subscriber.onComplete()
      })

    def onNext(elem: C): Future[Ack] = {
      var streamError = true
      try {
        val a = f(elem)
        streamError = false
        source.onNext(a)
      } catch {
        case NonFatal(ex) if streamError =>
          onError(ex)
          Stop
      }
    }

    def onError(ex: Throwable): Unit = source.onError(ex)

    def onComplete(): Unit = source.onComplete()
  }

}
