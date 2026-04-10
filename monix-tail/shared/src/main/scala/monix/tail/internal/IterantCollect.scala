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
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantCollect {
  /**
    * Implementation for `Iterant#collect`.
    */
  def apply[F[_], A, B](source: Iterant[F, A], pf: PartialFunction[A, B])(implicit F: Sync[F]): Iterant[F, B] = {

    val loop = new Loop[F, A, B](pf)
    source match {
      case Scope(_, _, _) | Suspend(_) | Halt(_) | Concat(_, _) =>
        loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }

  private final class Loop[F[_], A, B](pf: PartialFunction[A, B])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, B]] {

    def visit(ref: Next[F, A]): Iterant[F, B] = {
      val item = ref.item
      if (pf.isDefinedAt(item))
        Next[F, B](pf(item), ref.rest.map(this))
      else
        Suspend(ref.rest.map(this))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, B] =
      NextBatch(ref.batch.collect(pf), ref.rest.map(this))

    def visit(ref: NextCursor[F, A]): Iterant[F, B] =
      NextCursor(ref.cursor.collect(pf), ref.rest.map(this))

    def visit(ref: Suspend[F, A]): Iterant[F, B] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, B] = {
      val item = ref.item
      if (pf.isDefinedAt(item)) Last(pf(item))
      else Iterant.empty
    }

    def visit(ref: Halt[F, A]): Iterant[F, B] =
      ref.asInstanceOf[Iterant[F, B]]

    def fail(e: Throwable): Iterant[F, B] =
      Iterant.raiseError(e)
  }
}
