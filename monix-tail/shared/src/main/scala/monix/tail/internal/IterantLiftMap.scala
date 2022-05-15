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

import cats.arrow.FunctionK
import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantLiftMap {
  /** Implementation for `Iterant#liftMap`. */
  def apply[F[_], G[_], A](self: Iterant[F, A], f: FunctionK[F, G])(implicit G: Sync[G]): Iterant[G, A] = {

    Suspend(G.delay(new LoopK(f).apply(self)))
  }

  private final class LoopK[F[_], G[_], A](f: FunctionK[F, G])(implicit G: Sync[G])
    extends Iterant.Visitor[F, A, Iterant[G, A]] {

    def visit(ref: Next[F, A]): Iterant[G, A] =
      Next(ref.item, f(ref.rest).map(this))

    def visit(ref: NextBatch[F, A]): Iterant[G, A] =
      NextBatch(ref.batch, f(ref.rest).map(this))

    def visit(ref: NextCursor[F, A]): Iterant[G, A] =
      NextCursor(ref.cursor, f(ref.rest).map(this))

    def visit(ref: Suspend[F, A]): Iterant[G, A] =
      Suspend(f(ref.rest).map(this))

    def visit(ref: Concat[F, A]): Iterant[G, A] =
      Concat(f(ref.lh).map(this), f(ref.rh).map(this))

    def visit[S](ref: Scope[F, S, A]): Iterant[G, A] =
      Scope[G, S, A](
        f(ref.acquire),
        s => G.defer(f(ref.use(s)).map(this)),
        (s, exitCase) => f(ref.release(s, exitCase))
      )

    def visit(ref: Last[F, A]): Iterant[G, A] =
      ref.asInstanceOf[Iterant[G, A]]

    def visit(ref: Halt[F, A]): Iterant[G, A] =
      ref.asInstanceOf[Iterant[G, A]]

    def fail(e: Throwable): Iterant[G, A] =
      Iterant.raiseError(e)
  }
}
