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
      val NextCursor(cursor, rest) = ref
      val batchSize = cursor.recommendedBatchSize

      if (batchSize <= 1) {
        val item = cursor.next()
        Next(item, F.pure(ref).map(loop(prepend = true)))
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
          NextCursor(batchCursor, F.delay(loop(prepend = false)(ref)))
        } else {
          NextCursor(batchCursor, rest.map(loop(prepend = true)))
        }
      }
    }

    def loop(prepend: Boolean)(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case b @ Scope(_, _, _) =>
          b.runMap(loop(prepend))

        case halt @ Halt(_) => halt

        case Suspend(rest) =>
          Suspend(rest.map(loop(prepend)))

        case NextCursor(cursor, rest) if !cursor.hasNext() =>
          Suspend(rest.map(loop(prepend)))

        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          if (cursor.hasNext()) {
            val processed =
              processNonEmptyCursor(NextCursor(cursor, rest))
            if (prepend) {
              Next(separator, F.pure(processed))
            } else {
              processed
            }
          } else {
            Suspend(rest.map(loop(prepend)))
          }

        case _ if prepend => Next(
          separator,
          F.pure(source).map(loop(prepend = false))
        )

        case ref @ NextCursor(_, _) =>
          processNonEmptyCursor(ref)

        case Next(item, rest) =>
          Next(item, rest.map(loop(prepend = true)))

        case last @ Last(_) => last
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    source match {
      case NextCursor(_, _) | NextBatch(_, _) =>
        Suspend(F.delay(loop(prepend = false)(source)))
      case _ =>
        loop(prepend = false)(source)
    }
  }
}
