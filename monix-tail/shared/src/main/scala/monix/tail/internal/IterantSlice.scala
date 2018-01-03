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

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantSlice {
  /** Implementation for `Iterant#headOption`. */
  def headOptionL[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): F[Option[A]] = {

    def loop(source: Iterant[F, A]): F[Option[A]] = {
      try source match {
        case Next(a, _, stop) =>
          stop.map(_ => Some(a))

        case NextCursor(items, rest, stop) =>
          if (items.hasNext()) stop.map(_ => Some(items.next()))
          else rest.flatMap(loop)

        case NextBatch(items, rest, stop) =>
          val cursor = items.cursor()
          if (cursor.hasNext()) stop.map(_ => Some(cursor.next()))
          else rest.flatMap(loop)

        case Suspend(rest, _) =>
          rest.flatMap(loop)

        case Last(a) =>
          F.pure(Some(a))
        case Halt(None) =>
          F.pure(None)
        case Halt(Some(ex)) =>
          F.raiseError(ex)
      } catch {
        case NonFatal(ex) =>
          source.earlyStop.flatMap(_ =>
            F.raiseError(ex))
      }
    }

    source match {
      case NextCursor(_, _, _) | NextBatch(_, _, _) =>
        // Suspending execution for referential transparency, as we
        // can have side effects when processing NextCursor/NextBatch
        F.suspend(loop(source))
      case _ =>
        loop(source)
    }
  }
}
