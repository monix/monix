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
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTakeWhileWithIndex {
  def apply[F[_], A](source: Iterant[F, A], p: (A, Long) => Boolean) (implicit F: Sync[F]): Iterant[F, A] = {

    def processCursor(index: Long, ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val batchSize = cursor.recommendedBatchSize

      if (!cursor.hasNext())
        Suspend(rest.map(loop(index)))
      else if (batchSize <= 1) {
        val item = cursor.next()
        if (p(item, index)) Next(item, F.pure(ref).map(loop(index + 1)))
        else Iterant.empty
      }
      else {
        val buffer = ArrayBuffer.empty[A]
        var continue = true
        var cursorIndex = 0

        while (continue && cursorIndex < batchSize && cursor.hasNext()) {
          val item = cursor.next()
          if (p(item, index + cursorIndex)) {
            buffer += item
            cursorIndex += 1
          } else {
            continue = false
          }
        }

        val bufferCursor = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
        if (continue) {
          val next: F[Iterant[F, A]] = if (cursorIndex < batchSize) rest else F.pure(ref)
          NextCursor(bufferCursor, next.map(loop(index + cursorIndex)))
        } else {
          NextCursor(bufferCursor, F.pure(Iterant.empty))
        }
      }
    }

    def loop(index: Long)(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop(index))
        case Next(item, rest) =>
          if (p(item, index)) Next(item, rest.map(loop(index + 1)))
          else Iterant.empty
        case ref @ NextCursor(_, _) =>
          processCursor(index, ref)
        case NextBatch(batch, rest) =>
          processCursor(index, NextCursor(batch.cursor(), rest))
        case Suspend(rest) =>
          Suspend(rest.map(loop(index)))
        case Last(elem) =>
          if (p(elem, index)) Last(elem) else Iterant.empty
        case halt @ Halt(_) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }
    }

    source match {
      case Scope(_, _, _) | Suspend(_) | Halt(_) => loop(0)(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(0)(source)))
    }
  }
}
