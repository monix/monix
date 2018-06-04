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
import monix.tail.Iterant._

private[tail] object IterantCollect {
  /**
    * Implementation for `Iterant#collect`.
    */
  def apply[F[_], A, B](source: Iterant[F,A], pf: PartialFunction[A,B])
    (implicit F: Sync[F]): Iterant[F,B] = {

    def loop(source: Iterant[F,A]): Iterant[F,B] = {
      try source match {
        case Next(item, rest) =>
          if (pf.isDefinedAt(item)) Next[F,B](pf(item), rest.map(loop))
          else Suspend(rest.map(loop))

        case NextCursor(items, rest) =>
          val filtered = items.collect(pf)
          val restF = rest.map(loop)
          if (filtered.hasNext()) NextCursor(filtered, restF)
          else Suspend(restF)

        case NextBatch(items, rest) =>
          NextBatch(items.collect(pf), rest.map(loop))
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(item) =>
          if (pf.isDefinedAt(item)) Last(pf(item)) else Iterant.empty
        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, B]]
        case node =>
          node.runMap(loop)
      }
      catch {
        case ex if NonFatal(ex) =>
          Iterant.raiseError(ex)
      }
    }

    source match {
      case Scope(_, _, _) | Suspend(_) | Halt(_) => loop(source)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(loop(source)))
    }
  }
}
