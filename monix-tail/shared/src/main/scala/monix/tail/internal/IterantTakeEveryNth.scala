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
import monix.tail.internal.IterantUtils._

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantTakeEveryNth {
  /**
    * Implementation for `Iterant#takeEveryNth`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)
                    (implicit F: Sync[F]): Iterant[F, A] = {

    require(n > 0, "n must be strictly positive")

    if (n == 1) return source

    def processSeq(index: Int, ref: NextCursor[F, A]): NextCursor[F, A] = {
      val NextCursor(cursor, rest, stop) = ref
      val buffer = ArrayBuffer.empty[A]

      var idx = index
      var toProcess = cursor.recommendedBatchSize

      // protects against infinite cursors
      while (toProcess > 0 && cursor.hasNext()) {
        if (idx == 1) {
          buffer += cursor.next()
          idx = n
        } else {
          cursor.next()
          idx -= 1
        }
        toProcess -= 1
      }

      val next: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else rest
      NextCursor(BatchCursor.fromAnyArray(buffer.toArray[Any]), next.map(loop(idx)), stop)
    }


    def loop(index: Int)(source: Iterant[F,A]): Iterant[F,A] = {
      try source match {
        case Next(item, rest, stop) =>
          if (index == 1) Next(item, rest.map(loop(n)), stop)
          else Suspend(rest.map(loop(index - 1)), stop)
        case ref@NextCursor(_, _, _) =>
          processSeq(index, ref)
        case NextBatch(batch, rest, stop) =>
          processSeq(index, NextCursor(batch.cursor(), rest, stop))
        case Suspend(rest, stop) =>
          Suspend(rest.map(loop(index)), stop)
        case last @ Last(_) =>
          if (index == 1) last else Halt(None)
        case halt @ Halt(_) =>
          halt
      }
      catch {
        case ex if NonFatal(ex) => signalError(source, ex)
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
