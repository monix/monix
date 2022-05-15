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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTake {
  /**
    * Implementation for `Iterant#take`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {

    if (n > 0)
      Suspend(F.delay(new Loop[F, A](n).apply(source)))
    else
      Iterant.empty
  }

  private final class Loop[F[_], A](n: Int)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var toTake = n

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      toTake -= 1
      if (toTake > 0)
        Next(ref.item, ref.rest.map(this))
      else
        Last(ref.item)
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      // Aggregate cursor into an ArrayBuffer
      val max = math.min(cursor.recommendedBatchSize, toTake)
      val buffer = ArrayBuffer.empty[A]

      var idx = 0
      while (idx < max && cursor.hasNext()) {
        buffer += cursor.next()
        idx += 1
      }

      if (idx > 0) {
        toTake -= idx
        val restRef: F[Iterant[F, A]] =
          if (toTake > 0) {
            if (cursor.hasNext())
              F.pure(ref).map(this)
            else
              rest.map(this)
          } else {
            F.pure(Iterant.empty)
          }

        NextCursor(BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]], restRef)
      } else {
        Suspend(rest.map(this))
      }
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      Concat(
        ref.lh.map(this),
        F.defer {
          if (this.toTake > 0)
            ref.rh.map(this)
          else
            F.pure(Iterant.empty)
        })

    def visit[R](ref: Scope[F, R, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      toTake -= 1
      ref
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
