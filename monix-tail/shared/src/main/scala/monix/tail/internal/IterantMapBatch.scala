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
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.Batch

private[tail] object IterantMapBatch {
  /**
    * Implementation for `Iterant#mapBatch`
    */
  def apply[F[_], A, B](source: Iterant[F, A], f: A => Batch[B])(implicit F: Sync[F]): Iterant[F, B] = {

    val loop = new MapBatchLoop[F, A, B](f)
    source match {
      case Suspend(_) | Halt(_) | Concat(_, _) | Scope(_, _, _) =>
        // Fast path
        loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }

  /**
    * Describing the loop as a class because we can control memory
    * allocation better this way.
    */
  private final class MapBatchLoop[F[_], A, B](f: A => Batch[B])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, B]] { loop =>

    def visit(ref: Next[F, A]): Iterant[F, B] =
      NextBatch[F, B](f(ref.item), ref.rest.map(this))

    def visit(ref: NextBatch[F, A]): Iterant[F, B] =
      processBatch(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, B] =
      processBatch(ref)

    def visit(ref: Suspend[F, A]): Iterant[F, B] =
      Suspend[F, B](ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, B] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, B] =
      NextBatch(f(ref.item), F.delay(Halt[F, B](None)))

    def visit(ref: Halt[F, A]): Iterant[F, B] =
      ref.asInstanceOf[Iterant[F, B]]

    def fail(e: Throwable): Iterant[F, B] =
      Iterant.raiseError(e)

    def processBatch(ref: NextCursor[F, A]): Iterant[F, B] = {
      val NextCursor(cursor, rest) = ref

      if (cursor.hasNext()) {
        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(ref)
          else rest

        NextBatch(f(cursor.next()), next.map(loop))
      } else {
        Suspend(rest.map(loop))
      }
    }
  }
}
