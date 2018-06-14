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
import monix.execution.internal.collection.ArrayStack

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.batches.BatchCursor

private[tail] object IterantScanEval {
  /**
    * Implementation for `Iterant#scanEval`
    */
  def apply[F[_], A, S](source: Iterant[F, A], seed: F[S], ff: (S, A) => F[S])
    (implicit F: Sync[F]): Iterant[F, S] = {

    // Suspending execution in order to not trigger
    // side-effects accidentally
    val process = F.suspend {
      new Loop(seed, ff).start(source)
    }

    Suspend(process)
  }

  private class Loop[F[_], S, A](seed: F[S], ff: (S, A) => F[S])(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, S]) {
    private[this] var state: S = _
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()

    val start: Iterant[F, A] => F[Iterant[F, S]] = {
      case s@Scope(_, _, _) => s.runFlatMap(start)
      case Concat(lh, rh) =>
        stack.push(rh)
        lh.flatMap(start)
      case other =>
        seed.attempt.map {
          case Right(value) =>
            state = value
            this(other)
          case Left(ex) => Iterant.raiseError(ex)
        }
    }

    def apply(source: Iterant[F, A]): Iterant[F, S] = try source match {
      case Next(head, tail) =>
        protectedF(head, tail)

      case ref @ NextCursor(cursor, rest) =>
        evalNextCursor(ref, cursor, rest)

      case NextBatch(gen, rest) =>
        val cursor = gen.cursor()
        val ref = NextCursor(cursor, rest)
        evalNextCursor(ref, cursor, rest)

      case Suspend(rest) =>
        Suspend[F,S](rest.map(this))

      case s @ Scope(_, _, _) =>
        s.runMap(this)

      case Concat(lh, rh) =>
        stack.push(rh)
        Suspend(lh.map(this))

      case Last(item) =>
        stack.pop() match {
          case null =>
            val fa = ff(state, item)
            Suspend(fa.map(s => lastS[F,S](s)))
          case some =>
            protectedF(item, some)
        }

      case halt @ Halt(opt) =>
        val next = stack.pop()
        if (opt.nonEmpty || next == null) {
          halt.asInstanceOf[Iterant[F, S]]
        } else {
          Suspend(next.map(this))
        }
    } catch {
      case ex if NonFatal(ex) => Iterant.raiseError(ex)
    }

    private def protectedF(a: A, rest: F[Iterant[F, A]]): Iterant[F, S] = {
      val next = ff(state, a)
        .map { s =>
          state = s
          nextS(s, rest.map(this))
        }
        .handleError(Iterant.raiseError)

      Suspend(next)
    }

    private def evalNextCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext)
        Suspend[F, S](rest.map(this))
      else {
        val head = cursor.next()
        val tail = if (cursor.hasNext()) F.pure(ref: Iterant[F, A]) else rest
        protectedF(head, tail)
      }
    }
  }
}
