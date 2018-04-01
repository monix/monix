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
    * Implementation for `Iterant.bracketA`
    */
  def apply[F[_], A, B](
    acquire: F[A],
    use: A => Iterant[F, B],
    release: (A, BracketResult) => F[Unit]
  )(implicit F: Sync[F]): Iterant[F, B] = {

    def earlyRelease(a: A) =
      F.suspend(release(a, EarlyStop))

    def loop(a: A, sourceF: F[Iterant[F, B]]): F[Iterant[F, B]] =
      sourceF.map {
        case Next(item, rest, stop) =>
          Next(item, loop(a, rest), earlyRelease(a) *> stop)

        case NextCursor(cursor, rest, stop) =>
          NextCursor(cursor, loop(a, rest), earlyRelease(a) *> stop)

        case NextBatch(batch, rest, stop) =>
          NextBatch(batch, loop(a, rest), earlyRelease(a) *> stop)

        case Suspend(rest, stop) =>
          Suspend(
            loop(a, rest),
            earlyRelease(a) *> stop
          )

        case Last(value) =>
          val done = F.suspend(release(a, Completed))
          Suspend[F, B](
            F.pure(Iterant.nextS(value, done.as(Halt(None)), earlyRelease(a))),
            earlyRelease(a)
          )

        case h @ Halt(Some(ex)) =>
          val done = F.suspend(release(a, Error(ex)))
          Suspend[F, B](done.as(h), done)

        case h @ Halt(None) =>
          val done = F.suspend(release(a, Completed))
          Suspend[F, B](done.as(h), done)
      }

    def begin(a: A): F[Iterant[F, B]] =
      try {
        loop(a, F.pure(use(a)))
      } catch {
        case ex if NonFatal(ex) =>
          val end = F.suspend(release(a, Error(ex)))
            .as(Iterant.haltS[F, B](Some(ex)))
          F.pure(Suspend(end, F.unit))
      }

    Suspend(F.flatMap(acquire)(begin), F.unit)
  }
}
