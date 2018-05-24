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

private[tail] object IterantDrop {
  /**
    * Implementation for `Iterant#drop`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)
    (implicit F: Sync[F]): Iterant[F, A] = {

    // Reusable logic for NextCursor / NextBatch branches
    def dropFromCursor(toDrop: Int, ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val limit = math.min(cursor.recommendedBatchSize, toDrop)
      var droppedNow = 0

      while (droppedNow < limit && cursor.hasNext()) {
        cursor.next()
        droppedNow += 1
      }

      val next: F[Iterant[F, A]] = if (droppedNow == limit && cursor.hasNext()) F.pure(ref) else rest
      Suspend(next.map(loop(toDrop - droppedNow)))
    }

    def loop(toDrop: Int)(source: Iterant[F, A]): Iterant[F, A] = {
      try if (toDrop <= 0) source else source match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop(toDrop))
        case Next(_, rest) =>
          Suspend(rest.map(loop(toDrop - 1)))
        case ref @ NextCursor(_, _) =>
          dropFromCursor(toDrop, ref)
        case NextBatch(batch, rest) =>
          dropFromCursor(toDrop, NextCursor(batch.cursor(), rest))
        case Suspend(rest) =>
          Suspend(rest.map(loop(toDrop)))
        case Last(_) =>
          Iterant.empty
        case halt @ Halt(_) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }
    }

    source match {
      case NextBatch(_, _) | NextCursor(_, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(n)(source)))
      case _ =>
        loop(n)(source)
    }
  }
}
