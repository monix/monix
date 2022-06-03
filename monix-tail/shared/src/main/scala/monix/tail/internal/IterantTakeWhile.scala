/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTakeWhile {
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(p).apply(source)))
  }

  private class Loop[F[_], A](p: A => Boolean)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var isActive = true

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      val item = ref.item
      if (p(item))
        Next(item, ref.rest.map(this))
      else {
        isActive = false
        Iterant.empty
      }
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      visit(ref.toNextCursor())

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      Concat(
        ref.lh.map(this),
        F.defer {
          if (isActive)
            ref.rh.map(this)
          else
            F.pure(Iterant.empty)
        }
      )

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      val item = ref.item
      if (p(item)) ref
      else {
        isActive = false
        Iterant.empty
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val batchSize = cursor.recommendedBatchSize

      if (!cursor.hasNext())
        Suspend(rest.map(this))
      else if (batchSize <= 1) {
        val item = cursor.next()
        if (p(item))
          Next(item, F.pure(ref).map(this))
        else {
          isActive = false
          Iterant.empty
        }
      } else {
        val buffer = ArrayBuffer.empty[A]
        var continue = true
        var idx = 0

        while (continue && idx < batchSize && cursor.hasNext()) {
          val item = cursor.next()
          if (p(item)) {
            buffer += item
            idx += 1
          } else {
            continue = false
          }
        }

        val bufferCursor = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
        isActive = continue

        if (continue) {
          val next: F[Iterant[F, A]] = if (idx < batchSize) rest else F.pure(ref)
          NextCursor(bufferCursor, next.map(this))
        } else {
          NextCursor(bufferCursor, F.pure(Iterant.empty))
        }
      }
    }
  }
}
