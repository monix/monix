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

private[tail] object IterantTail {
  /**
    * Implementation for `Iterant#tail`
    */
  def apply[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): Iterant[F, A] = {

    def loop(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop)
        case Next(_, rest) =>
          Suspend(rest)
        case NextCursor(cursor, rest) =>
          if (cursor.hasNext()) {
            cursor.next()
            NextCursor(cursor, rest)
          } else {
            // If empty, then needs to retry with `rest`
            Suspend(rest.map(loop))
          }
        case NextBatch(batch, rest) =>
          // Unsafe recursive call, it's fine b/c next call won't be
          loop(NextCursor(batch.cursor(), rest))
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(_) =>
          Iterant.empty
        case halt @ Halt(_) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    source match {
      case NextBatch(_, _) | NextCursor(_, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(source)))
      case _ =>
        loop(source)
    }
  }
}
