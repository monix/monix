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

package monix.reactive.instances
import cats.Contravariant
import monix.execution.Ack
import monix.execution.Ack.Stop
import monix.reactive.Observer

import scala.concurrent.Future
import scala.util.control.NonFatal

/** `cats.Contravariant` type class instance for [[monix.reactive.Observer Observer]].
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  */
object CatsContravariantForObserver extends Contravariant[Observer] {
  override def contramap[A, B](fa: Observer[A])(f: B => A): Observer[B] =
    new ContravariantObserver(fa)(f)

  private final class ContravariantObserver[A, B](source: Observer[A])(f: B => A) extends Observer[B] {
    // For protecting the contract
    private[this] var isDone = false

    override def onNext(elem: B): Future[Ack] = {
      if (isDone) Stop
      else {
        var streamError = true
        try {
          val b = f(elem)
          streamError = false
          source.onNext(b)
        } catch {
          case NonFatal(ex) if streamError =>
            onError(ex)
            Stop
        }
      }
    }
    override def onError(ex: Throwable): Unit =
      if (!isDone) { isDone = true; source.onError(ex) }
    override def onComplete(): Unit =
      if (!isDone) { isDone = true; source.onComplete() }
  }
}