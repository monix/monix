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
import monix.tail.Iterant._
import monix.tail.internal.IterantUtils._
import monix.types.Applicative
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantCollect {
  /**
    * Implementation for `Iterant#collect`.
    */
  def apply[F[_], A, B](source: Iterant[F,A], pf: PartialFunction[A,B])
    (implicit A: Applicative[F]): Iterant[F,B] = {

    import A.{functor => F}

    def loop(source: Iterant[F,A]): Iterant[F,B] = {
      try source match {
        case Next(item, rest, stop) =>
          if (pf.isDefinedAt(item)) Next[F,B](pf(item), rest.map(loop), stop)
          else Suspend(rest.map(loop), stop)

        case NextSeq(items, rest, stop) =>
          val filtered = items.collect(pf)
          val restF = rest.map(loop)
          if (filtered.hasNext) NextSeq(filtered, restF, stop)
          else Suspend(restF, stop)

        case NextGen(items, rest, stop) =>
          NextGen(items.collect(pf), rest.map(loop), stop)

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)

        case Last(item) =>
          if (pf.isDefinedAt(item)) Last(pf(item)) else Halt(None)

        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, B]]
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }
    }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(A.eval(loop(source)), source.earlyStop)
    }
  }
}
