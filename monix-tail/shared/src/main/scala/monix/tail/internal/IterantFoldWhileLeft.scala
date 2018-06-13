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

package monix.tail
package internal

import cats.effect.Sync
import cats.syntax.all._
import scala.util.control.NonFatal

import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantFoldWhileLeft {
  /** Implementation for `Iterant.foldWhileLeftL`. */
  def strict[F[_], A, S](self: Iterant[F, A], seed: => S, f: (S, A) => Either[S, S])
    (implicit F: Sync[F]): F[S] = {
    F.suspend {
      new StrictLoop[F, A, S](seed, f).start(self)
    }
  }

  /** Implementation for `Iterant.foldWhileLeftEvalL`. */
  def eval[F[_], A, S](self: Iterant[F, A], seed: F[S], f: (S, A) => F[Either[S, S]])
    (implicit F: Sync[F]): F[S] = {

    F.suspend {
      new LazyLoop(seed, f).start(self)
    }
  }

  private class StrictLoop[F[_], A, S](seed: => S, f: (S, A) => Either[S, S])
    (implicit F: Sync[F])
    extends (Iterant[F, A] => F[S])
  {
    private[this] var state: S = null.asInstanceOf[S]
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()

    val start: Iterant[F, A] => F[S] = {
      case s@Scope(_, _, _) => s.runFold(start)
      case Concat(lh, rh) =>
        stack.push(rh)
        lh.flatMap(start)
      case other =>
        state = seed
        apply(other)
    }

    def apply(self: Iterant[F, A]): F[S] = {
      try self match {
        case Next(a, rest) =>
          f(state, a) match {
            case Left(s) =>
              state = s
              rest.flatMap(this)
            case Right(s) => F.pure(s)
          }

        case NextCursor(cursor, rest) =>
          process(cursor, rest)

        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          process(cursor, rest)

        case Suspend(rest) =>
          rest.flatMap(this)

        case b @ Scope(_, _, _) =>
          b.runFold(this)

        case Concat(lh, rh) =>
          stack.push(rh)
          lh.flatMap(this)

        case Last(a) =>
          stack.pop() match {
            case null => F.pure(f(state, a) match {
              case Left(s) => s
              case Right(s) => s
            })
            case rest => this(Next(a, rest))
          }

        case Halt(optE) =>
          optE match {
            case None =>
              stack.pop() match {
                case null =>
                  F.pure(state)
                case rest => rest.flatMap(this)
              }
            case Some(e) =>
              F.raiseError(e)
          }
      }
      catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }


    def process(cursor: BatchCursor[A], rest: F[Iterant[F, A]]): F[S] = {
      var hasResult = false

      while (!hasResult && cursor.hasNext()) {
        f(state, cursor.next()) match {
          case Left(s2) =>
            state = s2
          case Right(s2) =>
            hasResult = true
            state = s2
        }
      }

      if (hasResult)
        F.pure(state)
      else
        rest.flatMap(this)
    }
  }

  private class LazyLoop[F[_], A, S](seed: F[S], f: (S, A) => F[Either[S, S]])(
    implicit F: Sync[F]
  ) extends (Iterant[F, A] => F[S]) {
    private[this] var state: S = _
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()

    val start: Iterant[F, A] => F[S] = {
      case s @ Scope(_, _, _) => s.runFold(start)
      case Concat(lh, rh) =>
        stack.push(rh)
        lh.flatMap(start)
      case other =>
        seed.flatMap { result =>
          state = result
          apply(other)
        }
    }

    def apply(self: Iterant[F, A]): F[S] = {
      try self match {
        case Next(a, rest) =>
          process(rest, a)

        case NextCursor(cursor, rest) =>
          if (!cursor.hasNext()) rest.flatMap(this) else {
            val a = cursor.next()
            process(F.pure(self), a)
          }

        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          if (!cursor.hasNext()) rest.flatMap(this) else {
            val a = cursor.next()
            process(F.pure(NextCursor(cursor, rest)), a)
          }

        case Suspend(rest) =>
          rest.flatMap(this)

        case Concat(lh, rh) =>
          stack.push(rh)
          lh.flatMap(this)

        case b @ Scope(_, _, _) =>
          b.runFold(this)

        case Last(a) =>
          f(state, a).flatMap {
            case Left(s) =>
              stack.pop() match {
                case null => F.pure(s)
                case some =>
                  state = s
                  some.flatMap(this)
              }
            case Right(s) => F.pure(s)
          }

        case Halt(optE) =>
          optE match {
            case None =>
              stack.pop() match {
                case null => F.pure(state)
                case some => some.flatMap(this)
              }
            case Some(e) =>
              F.raiseError(e)
          }
      }
      catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }

    def process(rest: F[Iterant[F, A]], a: A): F[S] = {
      val fs = f(state, a)

      fs.flatMap {
        case Left(s) =>
          state = s
          rest.flatMap(this)
        case Right(s) => F.pure(s)
      }
    }
  }
}