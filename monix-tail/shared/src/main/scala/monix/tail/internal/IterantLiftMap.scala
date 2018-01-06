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

import cats.Applicative
import cats.arrow.FunctionK
import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantLiftMap {
  /** Implementation for `Iterant#liftMap`. */
  def apply[F[_], G[_], A](self: Iterant[F, A], f: FunctionK[F, G])
    (implicit G: Sync[G]): Iterant[G, A] = {

    // Note we can't do exception handling in this loop, because
    // if `f` throws then we are screwed since there's no way
    // to convert our `stop` from `F[Unit]` to `G[Unit]`. And then
    // we cannot signal a `Halt` without clean resource handling,
    // hence behavior needs to be undefined (i.e. it's the user's fault)

    def loop(fa: Iterant[F, A]): Iterant[G, A] =
      fa match {
        case Next(a, rest, stop) =>
          Next[G, A](a, f(rest).map(loop), f(stop))
        case NextBatch(a, rest, stop) =>
          NextBatch[G, A](a, f(rest).map(loop), f(stop))
        case NextCursor(a, rest, stop) =>
          NextCursor[G, A](a, f(rest).map(loop), f(stop))
        case Suspend(rest, stop) =>
          Suspend(f(rest).map(loop), f(stop))
        case Last(_) | Halt(_) =>
          fa.asInstanceOf[Iterant[G, A]]
      }

    loop(self)
  }

  /** Implementation for `Iterant#liftMap`. */
  def apply[F[_], G[_], A](self: Iterant[F, A], f: F[Iterant[F, A]] => G[Iterant[F, A]], g: F[Unit] => G[Unit])
    (implicit F: Applicative[F], G: Sync[G]): Iterant[G, A] = {

    // Note we can't do exception handling in this loop for `g`,
    // because if `g` throws then we are screwed since there's no way
    // to convert our `stop` from `F[Unit]` to `G[Unit]`. And then
    // we cannot signal a `Halt` without clean resource handling,
    // hence behavior needs to be undefined (i.e. it's the user's fault)

    def loop(fa: Iterant[F, A]): Iterant[G, A] =
      try fa match {
        case Next(a, rest, stop) =>
          Next[G, A](a, f(rest).map(loop), g(stop))
        case NextBatch(a, rest, stop) =>
          NextBatch[G, A](a, f(rest).map(loop), g(stop))
        case NextCursor(a, rest, stop) =>
          NextCursor[G, A](a, f(rest).map(loop), g(stop))
        case Suspend(rest, stop) =>
          Suspend(f(rest).map(loop), g(stop))
        case Last(_) | Halt(_) =>
          fa.asInstanceOf[Iterant[G, A]]
      } catch {
        case NonFatal(e) =>
          Suspend[G, A](g(fa.earlyStop).map(_ => Halt(Some(e))), G.unit)
      }

    loop(self)
  }
}
