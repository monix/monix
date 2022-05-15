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
import monix.tail.Iterant.{ Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantFoldRightL {
  /** Implementation for `Iterant.foldRightL`. */
  def apply[F[_], A, B](self: Iterant[F, A], b: F[B], f: (A, F[B]) => F[B])(implicit F: Sync[F]): F[B] =
    F.defer(new Loop(b, f).apply(self))

  private final class Loop[F[_], A, B](b: F[B], f: (A, F[B]) => F[B])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[B]] { self =>

    private[this] var remainder: Iterant[F, A] = _
    private[this] var suspendRef: F[B] = _

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used in visit(Concat)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def finish(): F[B] = {
      val rest =
        if (stackRef != null) stackRef.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      rest match {
        case null => b
        case xs => xs.flatMap(this)
      }
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[B] =
      f(ref.item, ref.rest.flatMap(this))

    def visit(ref: NextBatch[F, A]): F[B] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): F[B] = {
      val cursor = ref.cursor
      if (!cursor.hasNext())
        ref.rest.flatMap(this)
      else
        f(cursor.next(), suspend(ref))
    }

    def visit(ref: Suspend[F, A]): F[B] =
      ref.rest.flatMap(this)

    def visit(ref: Iterant.Concat[F, A]): F[B] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this)
    }

    def visit[S](ref: Scope[F, S, A]): F[B] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[B] =
      f(ref.item, finish())

    def visit(ref: Halt[F, A]): F[B] =
      ref.e match {
        case None => finish()
        case Some(e) =>
          F.raiseError(e)
      }

    def fail(e: Throwable): F[B] =
      F.raiseError(e)

    private def suspend(node: Iterant[F, A]): F[B] = {
      if (suspendRef == null) suspendRef = F.defer {
        self.remainder match {
          case null => fail(new NullPointerException("foldRight/remainder"))
          case rest => this.apply(rest)
        }
      }
      remainder = node
      suspendRef
    }
  }
}
