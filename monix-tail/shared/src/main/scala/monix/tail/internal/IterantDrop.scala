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
import monix.execution.internal.collection.ArrayStack
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
    extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): Iterant[F, A] =
      if (toDrop <= 0) {
        if (hasEmptyStack) ref
        else Next(ref.item, ref.rest.map(this))
      } else {
        toDrop -= 1
        Suspend(ref.rest.map(this))
      }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      if (toDrop <= 0) {
        if (hasEmptyStack) ref
        else NextBatch(ref.batch, ref.rest.map(this))
      } else {
        dropFromCursor(NextCursor(ref.batch.cursor(), ref.rest))
      }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      if (toDrop <= 0) {
        if (hasEmptyStack) ref
        else NextCursor(ref.cursor, ref.rest.map(this))
      } else {
        dropFromCursor(ref)
      }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      if (toDrop <= 0 && hasEmptyStack) ref
      else Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      if (toDrop <= 0 && hasEmptyStack) ref else {
        if (stack == null) stack = new ArrayStack()
        stack.push(ref.rh)
        Suspend(ref.lh.map(this))
      }

    def visit(ref: Scope[F, A]): Iterant[F, A] =
      if (toDrop <= 0 && hasEmptyStack) ref
      else ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      val rest =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      rest match {
        case null =>
          if (toDrop <= 0) ref
          else Iterant.empty

        case xs =>
          if (toDrop <= 0) {
            Next(ref.item, xs.map(this))
          } else {
            toDrop -= 1
            Suspend(xs.map(this))
          }
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref.e match {
        case None =>
          val rest =
            if (stack != null) stack.pop()
            else null.asInstanceOf[F[Iterant[F, A]]]

          rest match {
            case null => ref
            case xs => Suspend(xs.map(this))
          }
        case _ =>
          ref
      }

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    private def hasEmptyStack =
      stack == null || stack.isEmpty

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
