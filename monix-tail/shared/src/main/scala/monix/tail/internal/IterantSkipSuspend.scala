/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.types.Monad
import monix.types.syntax._

import scala.util.control.NonFatal

private[tail] object IterantSkipSuspend {
  /**
    * Implementation for `Iterant#skipSuspendL`
    */
  def apply[F[_], A](source: Iterant[F, A])(implicit F: Monad[F]): F[Iterant[F, A]] = {
    import F.{functor, applicative => A}

    def loop(source: Iterant[F, A]): F[Iterant[F, A]] =
      try source match {
        case Next(_, _, _) =>
          A.pure(source)
        case NextCursor(cursor, rest, stop) =>
          if (cursor.hasNext()) A.pure(source)
          else rest.flatMap(loop)
        case NextBatch(batch, rest, stop) =>
          val cursor = batch.cursor()
          loop(NextCursor(cursor, rest, stop))
        case Suspend(rest, stop) =>
          rest.flatMap(loop)
        case other @ (Halt(_) | Last(_)) =>
          A.pure(other)
      }
      catch {
        case NonFatal(ex) =>
          source.earlyStop.map(_ => Halt(Some(ex)))
      }

    // We can have side-effects with NextBatch/NextCursor
    // processing, so suspending execution in this case
    source match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        F.suspend(loop(source))
      case _ =>
        loop(source)
    }
  }
}
