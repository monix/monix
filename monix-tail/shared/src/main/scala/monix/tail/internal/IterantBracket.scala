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
import cats.syntax.functor._
import cats.syntax.apply._
import monix.execution.misc.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.{BracketResult, Iterant}
import monix.tail.BracketResult._

private[tail] object IterantBracket {
  /**
    * Implementation for `Iterant.bracket`
    */
  def apply[F[_], A, B](
    acquire: F[A],
    use: A => Iterant[F, B],
    release: (A, BracketResult) => F[Unit]
  )(implicit F: Sync[F]): Iterant[F, B] = {

    def earlyRelease(a: A) =
      F.suspend(release(a, EarlyStop))

    def loop(a: A)(source: Iterant[F, B]): Iterant[F, B] =
      source match {
        case Next(item, rest, stop) =>
          Next(item, rest.map(loop(a)), earlyRelease(a) *> stop)

        case NextCursor(cursor, rest, stop) =>
          NextCursor(cursor, rest.map(loop(a)), earlyRelease(a) *> stop)

        case NextBatch(batch, rest, stop) =>
          NextBatch(batch, rest.map(loop(a)), earlyRelease(a) *> stop)

        case Suspend(rest, stop) =>
          Suspend(
            F.handleError(rest.map(loop(a))) { ex =>
              val done = F.suspend(release(a, Error(ex)))
              Suspend(done.as(Halt(Some(ex))), done)
            },
            earlyRelease(a) *> stop
          )

        case h @ Last(_) =>
          Suspend(
            F.suspend(release(a, Completed)).as(h),
            earlyRelease(a)
          )

        case h @ Halt(Some(ex)) =>
          val done = F.suspend(release(a, Error(ex)))
          Suspend(done.as(h), done)

        case h @ Halt(None) =>
          val done = F.suspend(release(a, Completed))
          Suspend(done.as(h), done)
      }

    def begin(a: A) =
      try {
        loop(a)(use(a))
      } catch {
        case ex if NonFatal(ex) =>
          val end = F.suspend(release(a, Error(ex)))
            .as(Iterant.haltS[F, B](Some(ex)))
          Suspend(end, F.unit)
      }

    Suspend(acquire.map(begin), F.unit)
  }
}
