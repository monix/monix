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

package monix.tail
package internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.collection.ChunkedArrayStack

import scala.util.control.NonFatal
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantScan {
  /** Implementation for `Iterant#scan`. */
  def apply[F[_], A, S](fa: Iterant[F, A], initial: => S, f: (S, A) => S)(implicit F: Sync[F]): Iterant[F, S] = {
    // Given that `initial` is a by-name value, we have
    // to suspend
    val task = F.delay {
      try new Loop(initial, f).apply(fa)
      catch { case e if NonFatal(e) => Halt[F, S](Some(e)) }
    }
    Suspend(task)
  }

  class Loop[F[_], A, S](initial: S, f: (S, A) => S)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, S]] {

    private[this] var state = initial
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    def visit(ref: Next[F, A]): Iterant[F, S] = {
      state = f(state, ref.item)
      Next(state, ref.rest.map(this))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, S] =
      processCursor(ref.batch.cursor(), ref.rest)

    def visit(ref: NextCursor[F, A]): Iterant[F, S] =
      processCursor(ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): Iterant[F, S] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, S] = {
      stackPush(ref.rh)
      Suspend(ref.lh.map(this))
    }

    def visit[R](ref: Scope[F, R, A]): Iterant[F, S] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, S] = {
      state = f(state, ref.item)
      stackPop() match {
        case null => Last(state)
        case next => Next(state, next.map(this))
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, S] =
      ref.e match {
        case Some(_) =>
          ref.asInstanceOf[Iterant[F, S]]
        case None =>
          stackPop() match {
            case null =>
              ref.asInstanceOf[Iterant[F, S]]
            case next =>
              Suspend(next.map(this))
          }
      }

    def fail(e: Throwable): Iterant[F, S] =
      Iterant.raiseError(e)

    private[this] def processCursor(cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext()) {
        Suspend(rest.map(this))
      } else if (cursor.recommendedBatchSize <= 1) {
        state = f(state, cursor.next())
        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        Next(state, next.map(this))
      } else {
        val buffer = ArrayBuffer.empty[S]
        var toProcess = cursor.recommendedBatchSize

        while (toProcess > 0 && cursor.hasNext()) {
          state = f(state, cursor.next())
          buffer += state
          toProcess -= 1
        }

        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        val elems = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[S]]
        NextCursor(elems, next.map(this))
      }
    }
  }
}
