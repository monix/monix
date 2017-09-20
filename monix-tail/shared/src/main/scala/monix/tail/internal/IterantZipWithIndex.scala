/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import monix.tail.internal.IterantUtils._

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantZipWithIndex {
  /**
    * Implementation for `Iterant#zipWithIndex`
    */
  def apply[F[_], A](source: Iterant[F, A])
                    (implicit F: Sync[F]): Iterant[F, (A, Long)] = {


    def processSeq(index: Long, ref: NextCursor[F, A]): NextCursor[F, (A, Long)] = {
      val NextCursor(cursor, rest, stop) = ref
      val buffer = ArrayBuffer.empty[(A, Long)]

      var idx = index

      while (cursor.hasNext()) {
        buffer += ((cursor.next(), idx))
        idx += 1
      }

      NextCursor(BatchCursor.fromAnyArray(buffer.toArray[Any]), rest.map(loop(idx)), stop)
    }

    def loop(index: Long)(source: Iterant[F, A]): Iterant[F, (A, Long)] = {
      try source match {
        case Next(item, rest, stop) =>
          Next((item, index), rest.map(loop(index + 1)), stop)

        case Last(item) =>
          Last((item, index))

        case ref@NextCursor(_, _, _) =>
          processSeq(index, ref)

        case NextBatch(batch, rest, stop) =>
          processSeq(index, NextCursor(batch.cursor(), rest, stop))

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(index)), stop)

        case empty@Halt(_) =>
          empty.asInstanceOf[Iterant[F, (A, Long)]]
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }
    }

    source match {
      case Suspend(_, _) | Halt(_) => loop(0)(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(0)(source)), source.earlyStop)
    }
  }
}