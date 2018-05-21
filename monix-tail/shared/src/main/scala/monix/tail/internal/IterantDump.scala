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
import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.IterantUtils.signalError

private[tail] object IterantDump {
  /**
    * Implementation for `Iterant#dump`
    */
  def apply[F[_], A](source: Iterant[F, A], prefix: String, out: PrintStream = System.out)
    (implicit F: Sync[F]): Iterant[F, A] = {

    def moveNext(pos: Long, rest: F[Iterant[F, A]]): F[Iterant[F, A]] =
      rest.attempt.flatMap {
        case Left(e) =>
          out.println(s"$pos: $prefix --> effect error --> $e")
          F.raiseError(e)
        case Right(next) =>
          F.pure(loop(pos)(next))
      }

    def loop(pos: Long)(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case Next(item, rest, stop) =>
          out.println(s"$pos: $prefix --> next --> $item")
          Next[F, A](item, moveNext(pos + 1, rest), stop)

        case NextCursor(cursor, rest, stop) =>
          var cursorPos = pos
          val dumped = cursor.map { el =>
            out.println(s"$cursorPos: $prefix --> next-cursor --> $el")
            cursorPos += 1
            el
          }
          NextCursor[F, A](dumped, moveNext(cursorPos, rest), stop)

        case NextBatch(batch, rest, stop) =>
          var batchPos = pos
          val dumped = batch.map { el =>
            out.println(s"$batchPos: $prefix --> next-batch --> $el")
            batchPos += 1
            el
          }
          NextBatch[F, A](dumped, moveNext(batchPos, rest), stop)

        case Suspend(rest, stop) =>
          out.println(s"$pos: $prefix --> suspend")
          Suspend[F, A](moveNext(pos + 1, rest), stop)

        case Last(item) =>
          out.println(s"$pos: $prefix --> last --> $item")
          Last(item)

        case Halt(error) =>
          out.println(s"$pos: $prefix --> halt --> ${error.map(_.toString).getOrElse("no error")}")
          source

      } catch {
        case ex if NonFatal(ex) =>
          out.println(s"$pos: $prefix --> unexpected error --> $ex")
          signalError(source, ex)
      }

    Suspend(F.delay(loop(0)(source)), source.earlyStop)
  }
}