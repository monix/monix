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
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.types.Monad
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantCompleteL {
  /**
    * Implementation for `Iterant#completeL`
    */
  final def apply[F[_], A](source: Iterant[F, A])(implicit F: Monad[F]): F[Unit] = {
    import F.{functor, applicative => A}

    def loop(self: Iterant[F, A]): F[Unit] = {
      try self match {
        case Next(a, rest, stop) =>
          rest.flatMap(loop)
        case NextCursor(cursor, rest, stop) =>
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case NextBatch(gen, rest, stop) =>
          val cursor = gen.cursor()
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case Suspend(rest, stop) =>
          rest.flatMap(loop)
        case Last(item) =>
          A.unit
        case Halt(None) =>
          A.unit
        case Halt(Some(ex)) =>
          A.eval(throw ex)
      }
      catch {
        case NonFatal(ex) =>
          source.earlyStop.map(_ => throw ex)
      }
    }

    // If we have a cursor or a batch, then processing them
    // causes side-effects, so we need suspension of execution
    source match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        F.suspend(loop(source))
      case _ =>
        loop(source)
    }
  }
}
