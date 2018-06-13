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

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

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
        case Next(a, rest) =>
          Next[G, A](a, f(rest).map(loop))
        case NextBatch(a, rest) =>
          NextBatch[G, A](a, f(rest).map(loop))
        case NextCursor(a, rest) =>
          NextCursor[G, A](a, f(rest).map(loop))
        case Suspend(rest) =>
          Suspend(G.suspend(f(rest).map(loop)))
        case Scope(acquire, use, release) =>
          Scope(f(acquire), f(use).map(loop), exitCase => f(release(exitCase)))
        case Concat(lh, rh) =>
          Concat(f(lh).map(loop), f(rh).map(loop))
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
        case Next(a, rest) =>
          Next[G, A](a, f(rest).map(loop))
        case NextBatch(a, rest) =>
          NextBatch[G, A](a, f(rest).map(loop))
        case NextCursor(a, rest) =>
          NextCursor[G, A](a, f(rest).map(loop))
        case Suspend(rest) =>
          Suspend(f(rest).map(loop))
        case Scope(acquire, use, release) =>
          Scope(g(acquire), G.suspend(f(use).map(loop)), exitCase => g(release(exitCase)))
        case Concat(lh, rh) =>
          Concat(f(lh).map(loop), f(rh).map(loop))
        case Last(_) | Halt(_) =>
          fa.asInstanceOf[Iterant[G, A]]
      } catch {
        case e if NonFatal(e) =>
          Halt(Some(e))
      }

    loop(self)
  }
}
