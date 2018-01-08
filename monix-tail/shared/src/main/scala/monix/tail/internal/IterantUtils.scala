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

package monix.tail.internal

import cats.Functor
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantUtils {
  /** Internal utility for signaling an error, while
    * suspending `stop` before that.
    */
  def signalError[F[_], A, B](source: Iterant[F, A], e: Throwable)
    (implicit F: Functor[F]): Iterant[F,B] = {

    val halt = Iterant.haltS[F,B](Some(e))
    source match {
      case Next(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextCursor(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextBatch(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Suspend(_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Last(_) | Halt(_) =>
        halt
    }
  }

  /** Internal utility for signaling an error. */
  def signalError[F[_], A](stop: F[Unit])(e: Throwable)
    (implicit F: Functor[F]): F[Iterant[F, A]] =
    stop.map(_ => Halt[F, A](Some(e)))
}
