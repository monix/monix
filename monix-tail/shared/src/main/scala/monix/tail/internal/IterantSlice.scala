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
import monix.types.Monad
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantSlice {
  /** Implementation for `Iterant#headOption`. */
  def headOption[F[_], A](source: Iterant[F, A])(implicit F: Monad[F]): F[Option[A]] = {
    import F.{functor, applicative => A}

    def loop(source: Iterant[F, A]): F[Option[A]] = {
      try source match {
        case Next(a, rest, stop) =>
          stop.map(_ => Some(a))

        case NextSeq(items, rest, stop) =>
          if (items.hasNext) stop.map(_ => Some(items.next()))
          else rest.flatMap(loop)

        case NextGen(items, rest, stop) =>
          val cursor = items.iterator
          if (cursor.hasNext) stop.map(_ => Some(cursor.next()))
          else rest.flatMap(loop)

        case Suspend(rest, _) =>
          rest.flatMap(loop)

        case Last(a) =>
          A.pure(Some(a))
        case Halt(None) =>
          A.pure(None)
        case Halt(Some(ex)) =>
          A.unit.map(_ => throw ex)
      }
      catch {
        case NonFatal(ex) =>
          source.earlyStop.map(_ => throw ex)
      }
    }

    source match {
      case NextSeq(_, _, _) | NextGen(_, _, _) =>
        F.suspend(loop(source))
      case _ =>
        loop(source)
    }
  }
}
