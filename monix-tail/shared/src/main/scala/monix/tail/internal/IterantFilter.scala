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
import scala.util.control.NonFatal

import monix.tail.Iterant
import monix.tail.Iterant.{Scope, Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantFilter {
  /**
    * Implementation for `Iterant#filter`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
    (implicit F: Sync[F]): Iterant[F,A] = {

    def loop(source: Iterant[F,A]): Iterant[F,A] = {
      try source match {
        case b @ Scope(_, _, _) =>
          b.runMap(loop)

        case Next(item, rest) =>
          if (p(item)) Next(item, rest.map(loop))
          else Suspend(rest.map(loop))

        case NextCursor(items, rest) =>
          val filtered = items.filter(p)
          if (filtered.hasNext())
            NextCursor(filtered, rest.map(loop))
          else
            Suspend(rest.map(loop))

        case NextBatch(items, rest) =>
          NextBatch(items.filter(p), rest.map(loop))

        case Suspend(rest) =>
          Suspend(rest.map(loop))

        case last @ Last(item) =>
          if (p(item)) last else Iterant.empty

        case halt @ Halt(_) =>
          halt
      }
      catch {
        case ex if NonFatal(ex) => Iterant.raiseError(ex)
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
