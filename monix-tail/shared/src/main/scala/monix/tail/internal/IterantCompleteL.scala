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

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantCompleteL {
  /**
    * Implementation for `Iterant#completeL`
    */
  final def apply[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): F[Unit] = {

    def loop(self: Iterant[F, A]): F[Unit] = {
      try self match {
        case Next(_, rest, _) =>
          rest.flatMap(loop)
        case NextCursor(cursor, rest, _) =>
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case NextBatch(gen, rest, _) =>
          val cursor = gen.cursor()
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case Suspend(rest, _) =>
          rest.flatMap(loop)
        case Last(_) =>
          F.unit
        case Halt(None) =>
          F.unit
        case Halt(Some(ex)) =>
          F.raiseError(ex)
      } catch {
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
