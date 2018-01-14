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

import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.Iterant
import cats.syntax.all._
import cats.effect.Sync
import monix.execution.misc.NonFatal
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTake {
  /**
    * Implementation for `Iterant#take`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)
    (implicit F: Sync[F]): Iterant[F, A] = {

    def nextOrStop(rest: F[Iterant[F, A]], stop: F[Unit], n: Int, taken: Int): F[Iterant[F, A]] = {
      if (n > taken) rest.map(loop(n - taken))
      else stop.map(_ => Halt(None))
    }

    def processSeq(n: Int, ref: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest, stop) = ref
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
        NextCursor(BatchCursor.fromAnyArray(buffer.toArray[Any]), nextOrStop(restRef, stop, n, idx), stop)
      }
      else
        Suspend(nextOrStop(rest, stop, n, idx), stop)
    }

    def loop(n: Int)(source: Iterant[F, A]): Iterant[F, A] = {
      try if (n > 0) source match {
        case Next(elem, rest, stop) =>
          Next(elem, nextOrStop(rest, stop, n, 1), stop)
        case current @ NextCursor(_, _, _) =>
          processSeq(n, current)
        case NextBatch(batch, rest, stop) =>
          processSeq(n, NextCursor(batch.cursor(), rest, stop))
        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(n)), stop)
        case theEnd @ (Last(_) | Halt(_)) =>
          theEnd
      }
      else source match {
        case theEnd @ (Last(_) | Halt(_)) =>
          theEnd
        case other =>
          val stop = other.earlyStop
          Suspend(stop.map(_ => Halt(None)), stop)
      }
      catch {
        case ex if NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    source match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // We can have side-effects with NextBatch/NextCursor
        // processing, so suspending execution in this case
        Suspend(F.delay(loop(n)(source)), source.earlyStop)
      case _ =>
        loop(n)(source)
    }
  }
}
