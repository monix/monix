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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

private[tail] object IterantFilter {
  /**
    * Implementation for `Iterant#filter`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
    (implicit F: Sync[F]): Iterant[F,A] = {

    val loop = new Loop[F, A](p)

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

  private class Loop[F[_], A](p: A => Boolean)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A])
  {
    def apply(source: Iterant[F,A]): Iterant[F,A] = {
      try source match {
        case Next(item, rest) =>
          if (p(item)) Next(item, rest.map(this))
          else Suspend(rest.map(this))

        case NextCursor(items, rest) =>
          val filtered = items.filter(p)
          if (filtered.hasNext())
            NextCursor(filtered, rest.map(this))
          else
            Suspend(rest.map(this))

        case NextBatch(items, rest) =>
          NextBatch(items.filter(p), rest.map(this))

        case Suspend(rest) =>
          Suspend(rest.map(this))

        case node @ (Scope(_, _, _) | Concat(_, _)) =>
          node.runMap(this)

        case last @ Last(item) =>
          if (p(item)) last else Iterant.empty

        case halt @ Halt(_) =>
          halt
      }
      catch {
        case ex if NonFatal(ex) => Iterant.raiseError(ex)
      }
    }
  }
}
