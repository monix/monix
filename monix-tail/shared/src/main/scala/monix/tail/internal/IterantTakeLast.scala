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
import monix.execution.internal.collection.{ ChunkedArrayStack, DropHeadOnOverflowQueue }
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor

private[tail] object IterantTakeLast {
  /**
    * Implementation for `Iterant#takeLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    if (n < 1)
      Iterant.empty
    else
      Suspend(F.delay(new Loop(n).apply(source)))
  }

  private class Loop[F[_], A](n: Int)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] { loop =>

    private val buffer = DropHeadOnOverflowQueue.boxed[A](n)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      buffer.offer(ref.item)
      Suspend(ref.rest.map(loop))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] = {
      val cursor = ref.batch.cursor()
      while (cursor.hasNext()) buffer.offer(cursor.next())
      Suspend(ref.rest.map(loop))
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val cursor = ref.cursor
      while (cursor.hasNext()) buffer.offer(cursor.next())
      Suspend(ref.rest.map(loop))
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(loop))

    def visit(ref: Concat[F, A]): Iterant[F, A] = {
      stackPush(ref.rh)
      Suspend(ref.lh.map(this))
    }

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(loop)

    def visit(ref: Last[F, A]): Iterant[F, A] =
      stackPop() match {
        case null =>
          buffer.offer(ref.item)
          finalCursor()
        case some =>
          loop(Next(ref.item, some))
      }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref.e match {
        case None =>
          stackPop() match {
            case null => finalCursor()
            case some => Suspend(some.map(loop))
          }
        case _ =>
          ref
      }

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)

    private def finalCursor(): Iterant[F, A] = {
      val cursor = BatchCursor.fromIterator(buffer.iterator(true), Int.MaxValue)
      NextCursor(cursor, F.pure(Iterant.empty))
    }
  }
}
