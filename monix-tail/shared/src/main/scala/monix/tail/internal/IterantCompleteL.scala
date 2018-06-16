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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Resource, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantCompleteL {
  /**
    * Implementation for `Iterant#completeL`
    */
  final def apply[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F]): F[Unit] = {

    F.suspend(new Loop[F, A]().apply(source))
  }

  private final class Loop[F[_], A](implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[Unit]] {

    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): F[Unit] =
      ref.rest.flatMap(this)

    def visit(ref: NextBatch[F, A]): F[Unit] =
      processCursor(ref.batch.cursor(), ref.rest)

    def visit(ref: NextCursor[F, A]): F[Unit] =
      processCursor(ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): F[Unit] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Unit] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(ref.rh)
      ref.lh.flatMap(this)
    }

    def visit[S](ref: Resource[F, S, A]): F[Unit] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Unit] =
      continueOrFinish

    def visit(ref: Halt[F, A]): F[Unit] =
      ref.e match {
        case None => continueOrFinish
        case Some(e) => F.raiseError(e)
      }

    def fail(e: Throwable): F[Unit] =
      F.raiseError(e)

    private def continueOrFinish: F[Unit] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => F.unit
        case rest => rest.flatMap(this)
      }
    }

    private def processCursor(cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      while (cursor.hasNext()) cursor.next()
      rest.flatMap(this)
    }
  }
}
