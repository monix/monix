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
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantFoldRightL {
  /** Implementation for `Iterant.foldRightL`. */
  def apply[F[_], A, B](self: Iterant[F, A], b: F[B], f: (A, F[B], F[Unit]) => F[B])
    (implicit F: Sync[F]): F[B] = {

    def loop(self: Iterant[F, A]): F[B] = {
      try self match {
        case Next(a, rest, stop) =>
          f(a, rest.flatMap(loop), stop)

        case NextCursor(ref, rest, stop) =>
          if (!ref.hasNext())
            rest.flatMap(loop)
          else
            f(ref.next(), F.suspend(loop(self)), stop)

        case NextBatch(ref, rest, stop) =>
          loop(NextCursor(ref.cursor(), rest, stop))

        case Suspend(rest, _) =>
          rest.flatMap(loop)

        case Last(a) =>
          f(a, b, F.unit)

        case Halt(opt) =>
          opt match {
            case None => b
            case Some(e) => F.raiseError(e)
          }

      } catch {
        case NonFatal(e) =>
          self.earlyStop.flatMap(_ => F.raiseError(e))
      }
    }

    // Processing NextBatch/NextCursor might break
    // referential transparency, so suspending
    F.suspend(loop(self))
  }
}
