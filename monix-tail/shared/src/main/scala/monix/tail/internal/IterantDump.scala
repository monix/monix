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
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

import scala.util.control.NonFatal

private[tail] object IterantDump {
  /**
    * Implementation for `Iterant#dump`
    */
  def apply[F[_], A](source: Iterant[F, A], prefix: String, out: PrintStream = System.out)
                    (implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(prefix, out).apply(source)))
  }

  private class Loop[F[_], A](prefix: String, out: PrintStream)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) { loop =>

    private[this] var pos = 0L

    def moveNext(rest: F[Iterant[F, A]]): F[Iterant[F, A]] =
      rest.attempt.flatMap {
        case Left(e) =>
          out.println(s"$pos: $prefix --> effect error --> $e")
          F.raiseError(e)
        case Right(next) =>
          F.pure(loop(next))
      }

    def apply(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case Next(item, rest) =>
          out.println(s"$pos: $prefix --> next --> $item")
          pos += 1
          Next[F, A](item, moveNext(rest))

        case NextCursor(cursor, rest) =>
          val dumped = cursor.map { el =>
            out.println(s"$pos: $prefix --> next-cursor --> $el")
            pos += 1
            el
          }
          NextCursor[F, A](dumped, moveNext(rest))

        case NextBatch(batch, rest) =>
          val dumped = batch.map { el =>
            out.println(s"$pos: $prefix --> next-batch --> $el")
            pos += 1
            el
          }
          NextBatch[F, A](dumped, moveNext(rest))

        case Suspend(rest) =>
          out.println(s"$pos: $prefix --> suspend")
          pos += 1
          Suspend[F, A](moveNext(rest))

        case Last(item) =>
          out.println(s"$pos: $prefix --> last --> $item")
          Last(item)

        case Halt(error) =>
          out.println(s"$pos: $prefix --> halt --> ${error.map(_.toString).getOrElse("no error")}")
          source

        case c@Concat(lh, rh) =>
          out.println(s"$pos: $prefix --> concat --> ($lh, $rh)")
          pos += 1
          c.runMap(loop)

        case b@Scope(_, _, _) =>
          out.println(s"$pos: $prefix --> scope --> $b")
          pos += 1
          b.runMap(loop)

      } catch {
        case ex if NonFatal(ex) =>
          out.println(s"$pos: $prefix --> unexpected error --> $ex")
          Iterant.raiseError(ex)
      }

  }
}