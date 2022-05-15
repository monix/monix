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

import cats.syntax.all._
import cats.effect.Sync
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantFilter {
  /**
    * Implementation for `Iterant#filter`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)(implicit F: Sync[F]): Iterant[F, A] = {

    val loop = new Loop[F, A](p)
    source match {
      case Suspend(_) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }

  private class Loop[F[_], A](p: A => Boolean)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, A]] {

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      val item = ref.item
      if (p(item)) Next(item, ref.rest.map(this))
      else Suspend(ref.rest.map(this))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      NextBatch(ref.batch.filter(p), ref.rest.map(this))

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      NextCursor(ref.cursor.filter(p), ref.rest.map(this))

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] =
      if (p(ref.item)) ref else Halt(None)

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
