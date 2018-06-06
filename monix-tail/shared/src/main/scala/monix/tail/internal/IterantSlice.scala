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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

private[tail] object IterantSlice {
  /** Implementation for `Iterant#headOption`. */
  def headOptionL[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): F[Option[A]] = {

    def loop(source: Iterant[F, A]): F[Option[A]] = {
      try source match {
        case Next(a, _) =>
          a.some.pure[F]

        case NextCursor(items, rest) =>
          if (items.hasNext()) items.next().some.pure[F]
          else rest.flatMap(loop)

        case NextBatch(items, rest) =>
          val cursor = items.cursor()
          if (cursor.hasNext()) cursor.next().some.pure[F]
          else rest.flatMap(loop)

        case Suspend(rest) =>
          rest.flatMap(loop)

        case s @ Scope(_, _, _) =>
          s.runFold(loop)

        case Concat(lh, rh) =>
          lh.flatMap(loop).flatMap {
            case None => rh.flatMap(loop)
            case s @ Some(_) => F.pure(s)
          }

        case Last(a) =>
          F.pure(Some(a))
        case Halt(None) =>
          F.pure(None)
        case Halt(Some(ex)) =>
          F.raiseError(ex)
      } catch {
        case ex if NonFatal(ex) =>
          F.raiseError(ex)
      }
    }

    F.suspend {
      loop(source)
    }
  }
}