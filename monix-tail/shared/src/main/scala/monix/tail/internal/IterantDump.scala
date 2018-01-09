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

import java.io.PrintStream

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.IterantUtils.signalError

private[tail] object IterantDump {
  /**
    * Implementation for `Iterant#dump`
    */
  def apply[F[_], A](source: Iterant[F, A], prefix: String, out: PrintStream = System.out)
                    (implicit F: Sync[F]): Iterant[F, A] = {

    def loop(pos: Long)(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case Next(item, rest, stop) =>
          out.println(s"$pos: $prefix --> $item")
          Next[F, A](item, rest.map(loop(pos + 1)), stop)

        case NextCursor(cursor, rest, stop) =>
          var cursorPos = pos
          val dumped = cursor.map { el =>
            out.println(s"$cursorPos: $prefix --> $el")
            cursorPos += 1
            el
          }
          NextCursor[F, A](dumped, rest.map(loop(cursorPos)), stop)

        case NextBatch(batch, rest, stop) =>
          var batchPos = pos
          val dumped = batch.map { el =>
            out.println(s"$batchPos: $prefix --> $el")
            batchPos += 1
            el
          }
          NextBatch[F, A](dumped, rest.map(loop(batchPos)), stop)

        case Suspend(rest, stop) =>
          Suspend[F, A](rest.map(loop(pos)), stop)

        case Last(item) =>
          out.println(s"$pos: $prefix --> $item")
          out.println(s"${pos + 1}: $prefix completed")
          Last(item)

        case empty@Halt(_) =>
          out.println(s"$pos: $prefix completed")
          empty.asInstanceOf[Iterant[F, A]]

      } catch {
        case ex if NonFatal(ex) =>
          out.println(s"$pos: $prefix --> $ex")
          signalError(source, ex)
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(0)(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided PrintStream can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(0)(source)), source.earlyStop)
    }
  }
}