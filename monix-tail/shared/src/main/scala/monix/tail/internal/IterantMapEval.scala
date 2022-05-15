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
import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor

private[tail] object IterantMapEval {
  /**
    * Implementation for `Iterant#mapEval`
    */
  def apply[F[_], A, B](source: Iterant[F, A], ff: A => F[B])(implicit F: Sync[F]): Iterant[F, B] = {

    Suspend(F.delay(new Loop(ff).apply(source)))
  }

  private final class Loop[F[_], A, B](ff: A => F[B])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, B]] { self =>

    private[this] var restRef: F[Iterant[F, A]] = _
    private[this] val continueRef = (b: B) => nextS(b, self.restRef.map(self))

    private def continue(rest: F[Iterant[F, A]]) = {
      this.restRef = rest
      continueRef
    }

    def visit(ref: Next[F, A]): Iterant[F, B] = {
      val rest = ff(ref.item).map(continue(ref.rest))
      Suspend(rest)
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, B] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, B] =
      processCursor(ref, ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): Iterant[F, B] =
      Suspend[F, B](ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, B] =
      Suspend(ff(ref.item).map(b => Last(b)))

    def visit(ref: Halt[F, A]): Iterant[F, B] =
      ref.asInstanceOf[Iterant[F, B]]

    def fail(e: Throwable): Iterant[F, B] =
      Iterant.raiseError(e)

    private def processCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext()) {
        Suspend[F, B](rest.map(this))
      } else {
        val head = cursor.next()
        val fb =
          try ff(head)
          catch { case NonFatal(e) => F.raiseError[B](e) }
        // If the iterator is empty, then we can skip a beat
        val tail = if (cursor.hasNext()) F.pure(ref: Iterant[F, A]) else rest
        val suspended = fb.map(continue(tail))
        Suspend[F, B](suspended)
      }
    }
  }
}
