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
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantScanEval {
  /**
    * Implementation for `Iterant#scanEval`
    */
  def apply[F[_], A, S](source: Iterant[F, A], seed: F[S], ff: (S, A) => F[S])(implicit F: Sync[F]): Iterant[F, S] = {

    Suspend(seed.map { seed =>
      new Loop(seed, ff).apply(source)
    })
  }

  private class Loop[F[_], S, A](seed: S, ff: (S, A) => F[S])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, S]] {

    private[this] var state: S = seed
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    def visit(ref: Next[F, A]): Iterant[F, S] =
      processHead(ref.item, ref.rest)

    def visit(ref: NextBatch[F, A]): Iterant[F, S] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, S] = {
      val cursor = ref.cursor
      if (!cursor.hasNext())
        Suspend[F, S](ref.rest.map(this))
      else {
        val head = cursor.next()
        val tail = if (cursor.hasNext()) F.pure(ref: Iterant[F, A]) else ref.rest
        processHead(head, tail)
      }
    }

    def visit(ref: Suspend[F, A]): Iterant[F, S] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, S] = {
      stackPush(ref.rh)
      Suspend(ref.lh.map(this))
    }

    def visit[R](ref: Scope[F, R, A]): Iterant[F, S] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, S] =
      stackPop() match {
        case null =>
          val fa = ff(state, ref.item)
          Suspend(fa.map(s => lastS[F, S](s)))
        case some =>
          processHead(ref.item, some)
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

    private def processHead(a: A, rest: F[Iterant[F, A]]): Iterant[F, S] = {
      val next = ff(state, a).map { s =>
        state = s
        nextS(s, rest.map(this))
      }.handleError(Iterant.raiseError)

      Suspend(next)
    }
  }
}
