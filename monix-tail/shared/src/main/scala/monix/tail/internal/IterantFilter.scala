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

import cats.syntax.all._
import cats.effect.Sync
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.IterantUtils._

private[tail] object IterantFilter {
  /**
    * Implementation for `Iterant#filter`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
    (implicit F: Sync[F]): Iterant[F,A] = {

    def loop(source: Iterant[F,A]): Iterant[F,A] = {
      try source match {
        case Next(item, rest, stop) =>
          if (p(item)) Next(item, rest.map(loop), stop)
          else Suspend(rest.map(loop), stop)

        case NextCursor(items, rest, stop) =>
          val filtered = items.filter(p)
          if (filtered.hasNext())
            NextCursor(filtered, rest.map(loop), stop)
          else
            Suspend(rest.map(loop), stop)

        case NextBatch(items, rest, stop) =>
          NextBatch(items.filter(p), rest.map(loop), stop)

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)

        case last @ Last(item) =>
          if (p(item)) last else Halt(None)

        case halt @ Halt(_) =>
          halt
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }
    }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)), source.earlyStop)
    }
  }
}
