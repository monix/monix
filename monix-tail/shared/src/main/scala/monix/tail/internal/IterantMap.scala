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

import cats.syntax.all._
import cats.effect.Sync
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantMap {
  /**
    * Implementation for `Iterant#map`
    */
  def apply[F[_], A, B](source: Iterant[F, A], f: A => B)(implicit F: Sync[F]): Iterant[F, B] = {

    val loop = new Loop[F, A, B](f)
    source match {
      case Scope(_, _, _) | Suspend(_) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }

  private final class Loop[F[_], A, B](f: A => B)(implicit F: Sync[F]) extends Iterant.Visitor[F, A, Iterant[F, B]] {
    loop =>

    def visit(ref: Next[F, A]): Iterant[F, B] =
      Next[F, B](f(ref.item), ref.rest.map(loop))

    def visit(ref: NextBatch[F, A]): Iterant[F, B] =
      NextBatch(ref.batch.map(f), ref.rest.map(loop))

    def visit(ref: NextCursor[F, A]): Iterant[F, B] =
      NextCursor(ref.cursor.map(f), ref.rest.map(loop))

    def visit(ref: Suspend[F, A]): Iterant[F, B] =
      Suspend(ref.rest.map(loop))

    def visit(ref: Concat[F, A]): Iterant[F, B] =
      ref.runMap(loop)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, B] =
      ref.runMap(loop)

    def visit(ref: Last[F, A]): Iterant[F, B] =
      Last(f(ref.item))

    def visit(ref: Halt[F, A]): Iterant[F, B] =
      ref.asInstanceOf[Iterant[F, B]]

    def fail(e: Throwable): Iterant[F, B] =
      Iterant.raiseError(e)
  }
}
