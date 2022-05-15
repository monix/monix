/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantDrop {
  /**
    * Implementation for `Iterant#drop`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(n).apply(source)))
  }

  private final class Loop[F[_], A](n: Int)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var toDrop: Int = n

    def visit(ref: Next[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else {
        toDrop -= 1
        Suspend(ref.rest.map(this))
      }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else {
        dropFromCursor(ref.toNextCursor())
      }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else {
        dropFromCursor(ref)
      }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] =
      if (toDrop <= 0) ref
      else {
        toDrop -= 1
        Iterant.empty
      }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    // Reusable logic for NextCursor / NextBatch branches
    private def dropFromCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
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
  }
}
