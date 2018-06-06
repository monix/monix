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

import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.Iterant
import cats.syntax.all._
import cats.effect.Sync

import scala.util.control.NonFatal
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTake {
  /**
    * Implementation for `Iterant#take`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)
    (implicit F: Sync[F]): Iterant[F, A] = {

    type Stack = List[F[Iterant[F, A]]]

    def nextOrEmpty(rest: F[Iterant[F, A]], n: Int, taken: Int, stack: Stack): F[Iterant[F, A]] = {
      if (n > taken) rest.map(loop(n - taken, stack))
      else F.pure(Iterant.empty)
    }

    def processSeq(n: Int, ref: NextCursor[F, A], stack: Stack): Iterant[F, A] = {
      val NextCursor(cursor, rest) = ref
      // Aggregate cursor into an ArrayBuffer
      val toTake = math.min(cursor.recommendedBatchSize, n)
      val buffer = ArrayBuffer.empty[A]

      var idx = 0
      while (idx < toTake && cursor.hasNext()) {
        buffer += cursor.next()
        idx += 1
      }

      if (idx > 0) {
        val restRef: F[Iterant[F, A]] = if (idx < toTake) rest else F.pure(ref)
        NextCursor(
          BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]],
          nextOrEmpty(restRef, n, idx, stack))
      }
      else
        Suspend(nextOrEmpty(rest, n, idx, stack))
    }

    def loop(n: Int, stack: List[F[Iterant[F, A]]])(source: Iterant[F, A]): Iterant[F, A] = {
      try if (n > 0) source match {
        case Next(elem, rest) =>
          Next(elem, nextOrEmpty(rest, n, 1, stack))
        case current @ NextCursor(_, _) =>
          processSeq(n, current, stack)
        case NextBatch(batch, rest) =>
          processSeq(n, NextCursor(batch.cursor(), rest), stack)
        case Suspend(rest) =>
          Suspend(rest.map(loop(n, stack)))
        case s @ Scope(_, _, _) =>
          s.runMap(loop(n, stack))
        case Concat(lh, rh) =>
          Suspend(lh.map(loop(n, rh :: stack)))
        case Last(a) =>
          if (n == 1) source else
            stack match {
              case Nil => source
              case x :: xs => Next(a, x.map(loop(n - 1, xs)))
            }
        case Halt(opt) =>
          if (opt.nonEmpty) source
          else stack match {
            case Nil => source
            case x :: xs => Suspend(x.map(loop(n, xs)))
          }
      }
      else source match {
        case theEnd @ Halt(_) =>
          theEnd
        case _ =>
          Iterant.empty
      }
      catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }
    }

    source match {
      case NextBatch(_, _) | NextCursor(_, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(n, Nil)(source)))
      case _ =>
        loop(n, Nil)(source)
    }
  }
}
