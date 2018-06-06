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
import scala.util.control.NonFatal

import monix.tail.Iterant
import monix.tail.Iterant.{Scope, Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import scala.annotation.tailrec

private[tail] object IterantDropWhile {
  /**
    * Implementation for `Iterant#dropWhile`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
    (implicit F: Sync[F]): Iterant[F, A] = {

    // Reusable logic for NextCursor / NextBatch branches
    @tailrec
    def evalCursor(ref: F[Iterant[F, A]], cursor: BatchCursor[A], rest: F[Iterant[F, A]], dropped: Int): Iterant[F, A] = {
      if (!cursor.hasNext())
        Suspend(rest.map(loop))
      else if (dropped >= cursor.recommendedBatchSize)
        Suspend(ref.map(loop))
      else {
        val elem = cursor.next()
        if (p(elem))
          evalCursor(ref, cursor, rest, dropped + 1)
        else if (cursor.hasNext())
          Next(elem, ref)
        else
          Next(elem, rest)
      }
    }

    def loop(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case b @ Scope(_, _, _) =>
          b.runMap(loop)
        case ref @ Next(item, rest) =>
          if (p(item)) Suspend(rest.map(loop))
          else ref
        case ref @ NextCursor(cursor, rest) =>
          evalCursor(F.pure(ref), cursor, rest, 0)
        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          val ref = NextCursor(cursor, rest)
          evalCursor(F.pure(ref), cursor, rest, 0)
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case last @ Last(elem) =>
          if (p(elem)) Iterant.empty else last
        case halt @ Halt(_) =>
          halt
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    source match {
      case Suspend(_) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }
}
