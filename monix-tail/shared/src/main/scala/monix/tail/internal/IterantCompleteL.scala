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

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}


private[tail] object IterantCompleteL {
  /**
    * Implementation for `Iterant#completeL`
    */
  final def apply[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): F[Unit] = {

    def loop(source: Iterant[F, A]): F[Unit] = {
      try source match {
        case s @ Scope(_, _, _) =>
          s.runFold(loop)
        case Next(_, rest) =>
          rest.flatMap(loop)
        case NextCursor(cursor, rest) =>
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case NextBatch(gen, rest) =>
          val cursor = gen.cursor()
          while (cursor.hasNext()) cursor.next()
          rest.flatMap(loop)
        case Suspend(rest) =>
          rest.flatMap(loop)
        case Last(_) =>
          F.unit
        case Halt(None) =>
          F.unit
        case Halt(Some(ex)) =>
          F.raiseError(ex)
      } catch {
        case ex if NonFatal(ex) =>
          F.raiseError(ex)
      }
    }

    F.suspend { loop(source) }
  }
}
