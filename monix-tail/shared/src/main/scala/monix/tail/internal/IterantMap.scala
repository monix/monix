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

package monix.tail.internal

import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextGen, NextSeq, Suspend}
import monix.tail.internal.IterantUtils.signalError
import monix.types.Applicative
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantMap {
  /**
    * Implementation for `Iterant#map`
    */
  def apply[F[_], A, B](source: Iterant[F, A], f: A => B)
    (implicit F: Applicative[F]): Iterant[F, B] = {

    import F.functor

    def loop(source: Iterant[F, A]): Iterant[F, B] =
      try source match {
        case Next(head, tail, stop) =>
          Next[F, B](f(head), tail.map(_.map(f)), stop)

        case NextSeq(cursor, rest, stop) =>
          NextSeq[F, B](cursor.map(f), rest.map(_.map(f)), stop)

        case NextGen(gen, rest, stop) =>
          NextGen(gen.map(f), rest.map(_.map(f)), stop)

        case Suspend(rest, stop) =>
          Suspend[F, B](rest.map(_.map(f)), stop)

        case Last(item) =>
          Last(f(item))

        case empty@Halt(_) =>
          empty.asInstanceOf[Iterant[F, B]]
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(F.eval(loop(source)), source.earlyStop)
    }
  }
}
