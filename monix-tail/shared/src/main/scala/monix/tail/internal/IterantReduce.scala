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

package monix.tail
package internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantReduce {
  /** Implementation for `Iterant.reduce`. */
  def apply[F[_], A](self: Iterant[F, A], op: (A, A) => A)(implicit F: Sync[F]): F[Option[A]] = {

    F.defer { new Loop[F, A](op).apply(self) }
  }

  private class Loop[F[_], A](op: (A, A) => A)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, F[Option[A]]] {

    private[this] var isEmpty = true
    private[this] var state: A = _

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

    private[this] val concatContinue: (Option[A] => F[Option[A]]) =
      state =>
        stackPop() match {
          case null => F.pure(state)
          case xs => xs.flatMap(this)
        }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[Option[A]] = {
      if (isEmpty) {
        state = ref.item
        isEmpty = false
      } else {
        state = op(state, ref.item)
      }
      ref.rest.flatMap(this)
    }

    def visit(ref: NextBatch[F, A]): F[Option[A]] = {
      if (isEmpty) {
        visit(ref.toNextCursor())
      } else {
        state = ref.batch.foldLeft(state)(op)
        ref.rest.flatMap(this)
      }
    }

    def visit(ref: NextCursor[F, A]): F[Option[A]] = {
      if (isEmpty) {
        if (ref.cursor.hasNext()) {
          isEmpty = false
          state = ref.cursor.next()
          state = ref.cursor.foldLeft(state)(op)
        }
      } else {
        state = ref.cursor.foldLeft(state)(op)
      }
      ref.rest.flatMap(this)
    }

    def visit(ref: Suspend[F, A]): F[Option[A]] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Option[A]] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this).flatMap(concatContinue)
    }

    def visit[S](ref: Scope[F, S, A]): F[Option[A]] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Option[A]] = {
      if (isEmpty) {
        state = ref.item
        isEmpty = false
      } else {
        state = op(state, ref.item)
      }
      F.pure(Some(state))
    }

    def visit(ref: Halt[F, A]): F[Option[A]] =
      ref.e match {
        case None =>
          if (isEmpty) F.pure(None)
          else F.pure(Some(state))
        case Some(e) =>
          F.raiseError(e)
      }

    def fail(e: Throwable): F[Option[A]] =
      F.raiseError(e)
  }
}
