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

import scala.collection.mutable.ArrayBuffer

import cats.effect.Sync
import cats.syntax.functor._
import scala.util.control.NonFatal

import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor

private[tail] object IterantIntersperse {
  def apply[F[_], A](source: Iterant[F, A], separator: A)(implicit F: Sync[F]): Iterant[F, A] =
    Suspend {
      F.delay(new Loop[F, A](separator).apply(source))
    }

  private class Loop[F[_], A](separator: A)(implicit F: Sync[F]) extends (Iterant[F, A] => Iterant[F, A]) {
    private[this] var prepend = false
    private[this] val stack = ChunkedArrayStack[F[Iterant[F, A]]]()

    def apply(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
          case halt @ Halt(opt) =>
            val next = stack.pop()
            if (opt.nonEmpty || next == null) {
              halt
            } else {
              Suspend(next.map(this))
            }

          case Suspend(rest) =>
            Suspend(rest.map(this))

          case NextCursor(cursor, rest) if !cursor.hasNext() =>
            Suspend(rest.map(this))

          case NextBatch(batch, rest) if !batch.cursor().hasNext() =>
            Suspend(rest.map(this))

          case Concat(lh, rh) =>
            stack.push(rh)
            Suspend(lh.map(this))

          case b @ Scope(_, _, _) =>
            b.runMap(this)

          case _ if prepend =>
            prepend = false
            Next(separator, F.pure(source).map(this))

          case ref @ NextCursor(_, _) =>
            processNonEmptyCursor(ref)

          case NextBatch(batch, rest) =>
            processNonEmptyCursor(NextCursor(batch.cursor(), rest))

          case Next(item, rest) =>
            prepend = true
            Next(item, rest.map(this))

          case last @ Last(a) =>
            stack.pop() match {
              case null => last
              case some =>
                prepend = true
                Next(a, some.map(this))
            }
        }
      catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    def processNonEmptyCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val batchSize = cursor.recommendedBatchSize

      if (batchSize <= 1) {
        val item = cursor.next()
        prepend = true
        Next(item, F.delay(this(ref)))
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
          prepend = false
          NextCursor(batchCursor, F.delay(this(ref)))
        } else {
          prepend = true
          NextCursor(batchCursor, rest.map(this))
        }
      }
    }
  }
}
