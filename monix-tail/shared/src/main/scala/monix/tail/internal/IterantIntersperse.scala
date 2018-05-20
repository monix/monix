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

import scala.collection.mutable.ArrayBuffer

import cats.effect.Sync
import cats.syntax.functor._
import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor


private[tail] object IterantIntersperse {
  def apply[F[_], A](source: Iterant[F, A], separator: A)(implicit F: Sync[F]): Iterant[F, A] = {
    def processNonEmptyCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest, stop) = ref
      val batchSize = cursor.recommendedBatchSize

      if (batchSize <= 1) {
        val item = cursor.next()
        Next(item, F.pure(ref).map(loop(prepend = true)), stop)
      } else {
        var appends = 0
        val maxAppends = batchSize / 2
        val buffer = ArrayBuffer.empty[A]
        var continue = true
        while (continue && appends < maxAppends) {
          buffer += cursor.next()
          appends += 1
          if (cursor.hasNext()) {
            // only append separator if element is guaranteed to be not the last one
            buffer += separator
          } else {
            continue = false
          }
        }
        val batchCursor = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
        if (cursor.hasNext()) {
          // ref now contains mutated cursor, continue with it
          NextCursor(batchCursor, F.delay(loop(prepend = false)(ref)), stop)
        } else {
          NextCursor(batchCursor, rest.map(loop(prepend = true)), stop)
        }
      }
    }

    def loop(prepend: Boolean)(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case halt @ Halt(_) => halt

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(prepend)), stop)

        case NextCursor(cursor, rest, stop) if !cursor.hasNext() =>
          Suspend(rest.map(loop(prepend)), stop)

        case NextBatch(batch, rest, stop) =>
          val cursor = batch.cursor()
          if (cursor.hasNext()) {
            val processed =
              processNonEmptyCursor(NextCursor(cursor, rest, stop))
            if (prepend) {
              Next(separator, F.pure(processed), stop)
            } else {
              processed
            }
          } else {
            Suspend(rest.map(loop(prepend)), stop)
          }

        case _ if prepend => Next(
          separator,
          F.pure(source).map(loop(prepend = false)),
          source.earlyStop
        )

        case ref @ NextCursor(_, _, _) =>
          processNonEmptyCursor(ref)

        case Next(item, rest, stop) =>
          Next(item, rest.map(loop(prepend = true)), stop)

        case last @ Last(_) => last
      } catch {
        case ex if NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    source match {
      case NextCursor(_, _, _) | NextBatch(_, _, _) =>
        Suspend(F.delay(loop(prepend = false)(source)), source.earlyStop)
      case _ =>
        loop(prepend = false)(source)
    }
  }
}
