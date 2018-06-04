/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.Batch

private[tail] object IterantMapBatch {
  /**
    * Implementation for `Iterant#mapBatch`
    */
  def apply[F[_], A, B](source: Iterant[F, A], f: A => Batch[B])
    (implicit F: Sync[F]): Iterant[F, B] = {
    val loop = new MapBatchLoop(f)
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

    /**
      * Describing the loop as a class because we can control memory
      * allocation better this way.
      */
    private final class MapBatchLoop[F[_], A, B](f: A => Batch[B])
      (implicit F: Sync[F])
      extends (Iterant[F, A] => Iterant[F, B]) { loop =>

      def apply(node: Iterant[F, A]): Iterant[F, B] =
        try node match {
          case Next(head, tail) =>
            NextBatch[F, B](f(head), tail.map(loop))
          case ref@NextCursor(_, _) =>
            processBatch(ref)
          case NextBatch(batch, rest) =>
            processBatch(NextCursor(batch.cursor(), rest))
          case Suspend(rest) =>
            Suspend[F, B](rest.map(loop))
          case s @ (Scope(_, _, _) | Concat(_, _)) =>
            s.runMap(loop)
          case Last(item) =>
            NextBatch(f(item), F.delay(Halt[F, B](None)))
          case empty@Halt(_) =>
            empty.asInstanceOf[Iterant[F, B]]
        } catch {
          case ex if NonFatal(ex) => Iterant.raiseError(ex)
        }

      def processBatch(ref: NextCursor[F, A]): Iterant[F, B] = {
        val NextCursor(cursor, rest) = ref

        if(cursor.hasNext()) {
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