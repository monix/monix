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
import monix.execution.internal.collection.ArrayStack

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTakeWhile {
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
                    (implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(p).apply(source)))
  }

  private class Loop[F[_], A](p: A => Boolean)
                             (implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) { loop =>
    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def apply(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case Next(item, rest) =>
          handleNext(item, rest)
        case ref@NextCursor(_, _) =>
          processCursor(ref)
        case NextBatch(batch, rest) =>
          processCursor(NextCursor(batch.cursor(), rest))
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(elem) =>
          handleLast(elem)
        case halt@Halt(_) =>
          handleHalt(halt)
        case s@Scope(_, _, _) =>
          s.runMap(loop)
        case Concat(lh, rh) =>
          handleConcat(lh, rh)
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    private def handleNext(elem: A, rest: F[Iterant[F, A]]): Iterant[F, A] = {
      if (p(elem)) Next(elem, rest.map(loop))
      else {
        // gc relief
        stack = null
        Iterant.empty
        }
      }

    private def handleLast(elem: A): Iterant[F, A] = {
      if (p(elem)) {
        val next =
          if (stack != null) stack.pop()
          else null.asInstanceOf[F[Iterant[F, A]]]

        next match {
          case null => Last(elem)
          case stream => Next(elem, stream.map(loop))
        }
      } else {
        Iterant.empty
      }
    }

    private def handleHalt(halt: Halt[F, A]): Iterant[F, A] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => halt
        case stream => Suspend(stream.map(loop))
      }
    }

    private def handleConcat(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): Iterant[F, A] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(rh)
      Suspend(lh.map(loop))
    }

    def processCursor(ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      val batchSize = cursor.recommendedBatchSize

      if (!cursor.hasNext())
        Suspend(rest.map(loop))
      else if (batchSize <= 1) {
        val item = cursor.next()
        if (p(item)) Next(item, F.pure(ref).map(loop))
        else Iterant.empty
      }
      else {
        val buffer = ArrayBuffer.empty[A]
        var continue = true
        var idx = 0

        while (continue && idx < batchSize && cursor.hasNext()) {
          val item = cursor.next()
          if (p(item)) {
            buffer += item
            idx += 1
          } else {
            continue = false
          }
        }

        val bufferCursor = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
        if (continue) {
          val next: F[Iterant[F, A]] = if (idx < batchSize) rest else F.pure(ref)
          NextCursor(bufferCursor, next.map(loop))
        } else {
          NextCursor(bufferCursor, F.pure(Iterant.empty))
        }
      }
    }
  }

}
