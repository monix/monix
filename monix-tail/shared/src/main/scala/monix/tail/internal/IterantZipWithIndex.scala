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
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantZipWithIndex {
  /**
    * Implementation for `Iterant#zipWithIndex`
    */
  def apply[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, (A, Long)] = {
    Suspend(F.delay(new Loop().apply(source)))
  }

  private class Loop[F[_], A](implicit F: Sync[F]) extends (Iterant[F, A] => Iterant[F, (A, Long)]) {

    private[this] var index = 0L

    private[this] def processSeq(ref: NextCursor[F, A]): NextCursor[F, (A, Long)] = {
      val NextCursor(cursor, rest) = ref
      val buffer = ArrayBuffer.empty[(A, Long)]

      var toProcess = cursor.recommendedBatchSize

      // protects against infinite cursors
      while (toProcess > 0 && cursor.hasNext()) {
        buffer += ((cursor.next(), index))
        index += 1
        toProcess -= 1
      }

      val next: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else rest
      NextCursor(
        BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[(A, Long)]],
        next.map(this)
      )
    }

    def apply(source: Iterant[F, A]): Iterant[F, (A, Long)] = {
      try source match {
          case Next(item, rest) =>
            val r = Iterant.nextS((item, index), rest.map(this))
            index += 1
            r

          case Last(item) =>
            val r = Iterant.lastS[F, (A, Long)]((item, index))
            index += 1
            r

          case ref @ NextCursor(_, _) =>
            processSeq(ref)

          case NextBatch(batch, rest) =>
            processSeq(NextCursor(batch.cursor(), rest))

          case Suspend(rest) =>
            Suspend(rest.map(this))

          case empty @ Halt(_) =>
            empty.asInstanceOf[Iterant[F, (A, Long)]]

          case node @ Scope(_, _, _) =>
            node.runMap(this)

          case node @ Concat(_, _) =>
            node.runMap(this)
        }
      catch {
        case ex if NonFatal(ex) => Iterant.raiseError(ex)
      }
    }
  }
}
