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

private[tail] object IterantDrop {
  /**
    * Implementation for `Iterant#drop`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)
    (implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(n).apply(source)))
  }

  private class Loop[F[_], A](private[this] var toDrop: Int)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) {
    // Reusable logic for NextCursor / NextBatch branches
    private[this] def dropFromCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val limit = math.min(cursor.recommendedBatchSize, toDrop)
      var droppedNow = 0

      while (droppedNow < limit && cursor.hasNext()) {
        cursor.next()
        droppedNow += 1
        toDrop -= 1
      }

      val next: F[Iterant[F, A]] = if (droppedNow == limit && cursor.hasNext()) F.pure(ref) else rest
      Suspend(next.map(this))
    }

    def apply(source: Iterant[F, A]): Iterant[F, A] = {
      try if (toDrop <= 0) source else source match {
        case Next(_, rest) =>
          toDrop -= 1
          Suspend(rest.map(this))
        case ref @ NextCursor(_, _) =>
          dropFromCursor(ref)
        case NextBatch(batch, rest) =>
          dropFromCursor(NextCursor(batch.cursor(), rest))
        case Suspend(rest) =>
          Suspend(rest.map(this))
        case Last(_) =>
          toDrop -= 1
          Iterant.empty
        case halt @ Halt(_) =>
          halt
        case s @ (Scope(_, _, _) | Concat(_, _)) =>
          s.runMap(this)
      } catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }
    }
  }
}
