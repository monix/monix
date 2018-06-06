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

import cats.syntax.all._
import cats.effect.Sync

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

private[tail] object IterantSkipSuspend {
  /**
    * Implementation for `Iterant#skipSuspendL`
    */
  def apply[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[Iterant[F, A]] = {
    def loop(source: Iterant[F, A]): F[Iterant[F, A]] =
      try source match {
        case Concat(lh, rh) =>
          lh.flatMap(loop).flatMap {
            case halt @ Halt(opt) =>
              if (opt.isEmpty) rh.flatMap(loop)
              else F.pure(halt)
            case other =>
              F.pure(Concat(F.pure(other), rh))
          }
        case Next(_, _) =>
          F.pure(source)
        case NextCursor(cursor, rest) =>
          if (cursor.hasNext()) F.pure(source)
          else rest.flatMap(loop)
        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          loop(NextCursor(cursor, rest))
        case Suspend(rest) =>
          rest.flatMap(loop)
        case other @ (Halt(_) | Last(_) | Scope(_, _, _)) =>
          // We can't safely remove Suspend nodes inside a Scope,
          // since their evaluation might rely on resources not yet acquired
          F.pure(other)
      } catch {
        case ex if NonFatal(ex) =>
          F.pure(Halt(Some(ex)))
      }

    F.suspend { loop(source) }
      .handleError(ex => Halt(Some(ex)))
  }
}
