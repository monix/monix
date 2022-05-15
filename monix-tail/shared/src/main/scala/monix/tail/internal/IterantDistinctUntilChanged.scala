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

import cats.Eq
import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantDistinctUntilChanged {
  /**
    * Implementation for `distinctUntilChangedByKey`.
    */
  def apply[F[_], A, K](self: Iterant[F, A], f: A => K)(implicit F: Sync[F], K: Eq[K]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(f).apply(self)))
  }

  private class Loop[F[_], A, K](f: A => K)(implicit F: Sync[F], K: Eq[K])
    extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var current: K = null.asInstanceOf[K]

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      val a = ref.item
      val k = f(a)
      if (current == null || K.neqv(current, k)) {
        current = k
        Next(a, ref.rest.map(this))
      } else {
        Suspend(ref.rest.map(this))
      }
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      processCursor(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      processCursor(ref)

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      val a = ref.item
      val k = f(a)

      if (current == null || K.neqv(current, k)) {
        current = k
        ref
      } else {
        Iterant.empty
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    private def processCursor(self: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = self

      if (!cursor.hasNext()) {
        Suspend(rest.map(this))
      } else if (cursor.recommendedBatchSize <= 1) {
        val a = cursor.next()
        val k = f(a)
        if (current == null || K.neqv(current, k)) {
          current = k
          Next(a, F.delay(this(self)))
        } else
          Suspend(F.delay(this(self)))
      } else {
        val buffer = ArrayBuffer.empty[A]
        var count = cursor.recommendedBatchSize

        // We already know hasNext == true
        var continue = true
        while (continue) {
          val a = cursor.next()
          val k = f(a)
          count -= 1

          if (current == null || K.neqv(current, k)) {
            current = k
            buffer += a
          }

          continue = count > 0 && cursor.hasNext()
        }

        val next =
          if (cursor.hasNext())
            F.delay(this(self))
          else
            rest.map(this)

        if (buffer.isEmpty)
          Suspend(next)
        else {
          val ref = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
          NextCursor(ref, next)
        }
      }
    }
  }
}
