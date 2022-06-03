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
import monix.tail.Iterant.{ Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTakeEveryNth {
  /**
    * Implementation for `Iterant#takeEveryNth`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    if (n < 1)
      Iterant.raiseError(new IllegalArgumentException(s"takeEveryNth($n)"))
    else if (n == 1)
      source
    else
      Suspend(F.delay(new Loop[F, A](n).apply(source)))
  }

  private final class Loop[F[_], A](n: Int)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {
    private[this] var index = n

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      if (index == 1) {
        index = n
        Next(ref.item, ref.rest.map(this))
      } else {
        index -= 1
        Suspend(ref.rest.map(this))
      }
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      processSeq(index, ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      processSeq(index, ref)

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Iterant.Concat[F, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      if (index == 1) {
        index = n
        ref
      } else {
        index -= 1
        Iterant.empty
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    private def processSeq(index: Int, ref: NextCursor[F, A]): NextCursor[F, A] = {
      val NextCursor(cursor, rest) = ref
      val buffer = ArrayBuffer.empty[A]

      var idx = index
      var toProcess = cursor.recommendedBatchSize

      // protects against infinite cursors
      while (toProcess > 0 && cursor.hasNext()) {
        if (idx == 1) {
          buffer += cursor.next()
          idx = n
        } else {
          cursor.next()
          idx -= 1
        }
        toProcess -= 1
      }

      val next: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else rest
      this.index = idx

      NextCursor(BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]], next.map(this))
    }
  }
}
