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

import cats.syntax.functor._
import cats.effect.Sync
import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._

// TODO Scope will be left open until backup is consumed - although empty scopes are unlikely
private[tail] object IterantSwitchIfEmpty {
  def apply[F[_], A](primary: Iterant[F, A], backup: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {
    def loop(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop)
        case Suspend(rest) => Suspend(rest.map(loop))
        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          if (!cursor.hasNext()) {
            Suspend(rest.map(loop))
          } else {
            NextCursor(cursor, rest)
          }

        case NextCursor(cursor, rest) if !cursor.hasNext() =>
          Suspend(rest.map(loop))

        case Halt(None) => backup
        case _ => source
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }

    primary match {
      case NextBatch(_, _) | NextCursor(_, _) =>
        Suspend(F.delay(loop(primary)))
      case _ => loop(primary)
    }
  }
}
