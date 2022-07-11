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
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor

private[tail] object IterantCompleteL {
  /**
    * Implementation for `Iterant#completedL`
    */
  final def apply[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[Unit] = {

    F.defer(new Loop[F, A]().apply(source))
  }

  private final class Loop[F[_], A](implicit F: Sync[F]) extends Iterant.Visitor[F, A, F[Unit]] {

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used in visit(Concat)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    private[this] val concatContinue: (Unit => F[Unit]) =
      _ =>
        stackPop() match {
          case null => F.unit
          case xs => xs.flatMap(this)
        }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[Unit] =
      ref.rest.flatMap(this)

    def visit(ref: NextBatch[F, A]): F[Unit] =
      processCursor(ref.batch.cursor(), ref.rest)

    def visit(ref: NextCursor[F, A]): F[Unit] =
      processCursor(ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): F[Unit] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Unit] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this).flatMap(concatContinue)
    }

    def visit[S](ref: Scope[F, S, A]): F[Unit] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Unit] =
      F.unit

    def visit(ref: Halt[F, A]): F[Unit] =
      ref.e match {
        case None => F.unit
        case Some(e) => F.raiseError(e)
      }

    def fail(e: Throwable): F[Unit] =
      F.raiseError(e)

    private def processCursor(cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      while (cursor.hasNext()) cursor.next()
      rest.flatMap(this)
    }
  }
}
