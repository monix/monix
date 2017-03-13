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

package monix.eval.internal

import monix.eval.Iterant.{Halt, Last, Next, NextGen, NextSeq, Suspend}
import monix.eval.internal.IterantStop.earlyStop
import monix.eval.{Iterant, Task}
import scala.util.control.NonFatal

private[eval] object IterantFoldWhile {
  /** Implementation for `Iterant.foldWhileL` */
  def apply[A,S](source: Iterant[A], seed: => S)(f: (S, A) => Either[S, S]): Task[S] = {
    // Handles sequences
    def evalNextSeq(state: S, items: Iterator[A], rest: Task[Iterant[A]], stop: Task[Unit]): Task[S] = {
      var currentState = state
      var continue = true

      while (continue && items.hasNext) {
        val item = items.next()
        f(currentState, item) match {
          case Left(newState) =>
            currentState = newState
          case Right(newState) =>
            currentState = newState
            continue = false
        }
      }

      if (continue) rest.flatMap(loop(_, currentState))
      else stop.flatMap(_ => Task.now(currentState))
    }

    def loop(self: Iterant[A], state: S): Task[S] = {
      try self match {
        case Next(a, rest, stop) =>
          f(state, a) match {
            case Left(newState) =>
              rest.flatMap(loop(_, newState))
            case Right(newState) =>
              stop.flatMap(_ => Task.now(newState))
          }

        case NextSeq(cursor, rest, stop) =>
          evalNextSeq(state, cursor, rest, stop)

        case NextGen(gen, rest, stop) =>
          evalNextSeq(state, gen.iterator, rest, stop)

        case Suspend(rest, stop) =>
          rest.flatMap(loop(_, state))

        case Last(a) =>
          f(state, a) match {
            case Left(newState) => Task.now(newState)
            case Right(newState) => Task.now(newState)
          }

        case Halt(None) =>
          Task.now(state)

        case Halt(Some(ex)) =>
          Task.raiseError(ex)
      }
      catch {
        case NonFatal(ex) =>
          earlyStop(source).flatMap(_ => Task.raiseError(ex))
      }
    }

    Task.defer {
      var catchErrors = true
      try {
        val init = seed
        catchErrors = false
        loop(source, init)
      }
      catch {
        case NonFatal(ex) if catchErrors =>
          earlyStop(source).flatMap(_ => Task.raiseError(ex))
      }
    }
  }
}
