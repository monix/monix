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
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.IterantUtils.signalError
import cats.syntax.all._

private[tail] object IterantRepeat {
  /** Implementation for `Iterant.repeat`. */
  def apply[F[_], A, B](source: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {

    def loop(self: Iterant[F, A]): Iterant[F, A] =
      try self match {
        case Next(head, tail, stop) =>
          Next[F, A](head, tail.map(loop), stop)
        case NextCursor(cursor, rest, stop) =>
          NextCursor[F, A](cursor, rest.map(loop), stop)
        case NextBatch(gen, rest, stop) =>
          NextBatch(gen, rest.map(loop), stop)
        case Suspend(rest, stop) =>
          Suspend[F, A](rest.map(loop), stop)
        case Last(item) =>
          Next[F, A](item, F.delay(loop(Iterant.empty)), F.unit)
        case Halt(Some(ex)) =>
          signalError(self, ex)
        case Halt(None) =>
          Suspend(F.delay(loop(source)), self.earlyStop)
      } catch {
        case ex if NonFatal(ex) => signalError(source, ex)
      }

    source match {
      // terminate if the source is empty
      case empty @ Halt(_) => empty
      case _ => loop(source)
    }
  }
}