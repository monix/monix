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

private[tail] object IterantTail {
  /**
    * Implementation for `Iterant#tail`
    */
  def apply[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): Iterant[F, A] = {

    def loop(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case Next(_, rest, stop) =>
          Suspend(rest, stop)
        case NextCursor(cursor, rest, stop) =>
          if (cursor.hasNext()) {
            cursor.next()
            NextCursor(cursor, rest, stop)
          } else {
            // If empty, then needs to retry with `rest`
            Suspend(rest.map(loop), stop)
          }
        case NextBatch(batch, rest, stop) =>
          // Unsafe recursive call, it's fine b/c next call won't be
          loop(NextCursor(batch.cursor(), rest, stop))
        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)
        case Last(_) =>
          Halt(None)
        case halt @ Halt(_) =>
          halt
      } catch {
        case NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    source match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(source)), source.earlyStop)
      case _ =>
        loop(source)
    }
  }
}
