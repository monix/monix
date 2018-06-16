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
import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantFoldWhileLeft {
  /**
    * Implementation for `Iterant.foldWhileLeftL`.
    */
  def strict[F[_], A, S](self: Iterant[F, A], seed: => S, f: (S, A) => Either[S, S])
    (implicit F: Sync[F]): F[S] = {

    F.delay(seed).flatMap { state => new StrictLoop(state, f).apply(self) }
  }

  /**
    * Implementation for `Iterant.foldWhileLeftEvalL`.
    */
  def eval[F[_], A, S](self: Iterant[F, A], seed: F[S], f: (S, A) => F[Either[S, S]])
    (implicit F: Sync[F]): F[S] = {

    seed.flatMap { state => new LazyLoop(state, f).apply(self) }
  }

  private class StrictLoop[F[_], A, S](seed: S, f: (S, A) => Either[S, S])
    (implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[S]] {

    private[this] var state: S = seed
    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): F[S] =
      f(state, ref.item) match {
        case Left(s) =>
          state = s
          ref.rest.flatMap(this)
        case Right(s) =>
          F.pure(s)
      }

    def visit(ref: NextBatch[F, A]): F[S] =
      process(ref.batch.cursor(), ref.rest)

    def visit(ref: NextCursor[F, A]): F[S] =
      process(ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): F[S] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[S] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(ref.rh)
      ref.lh.flatMap(this)
    }

    def visit(ref: Scope[F, A]): F[S] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[S] =
      f(state, ref.item) match {
        case Right(s) => F.pure(s)
        case Left(s) =>
          stackPop() match {
            case null => F.pure(s)
            case xs =>
              state = s
              xs.flatMap(this)
          }
      }

    def visit(ref: Halt[F, A]): F[S] =
      ref.e match {
        case None =>
          stackPop() match {
            case null => F.pure(state)
            case xs => xs.flatMap(this)
          }
        case Some(e) =>
          F.raiseError(e)
      }

    def fail(e: Throwable): F[S] =
      F.raiseError(e)

    private def stackPop(): F[Iterant[F, A]] = {
      if (stack != null) stack.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
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

  private class LazyLoop[F[_], A, S](seed: S, f: (S, A) => F[Either[S, S]])
    (implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[S]] {

    private[this] var state: S = seed
    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): F[S] =
      f(state, ref.item).flatMap {
        case Left(s) =>
          state = s
          ref.rest.flatMap(this)
        case Right(s) =>
          F.pure(s)
      }

    def visit(ref: NextBatch[F, A]): F[S] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): F[S] = {
      val cursor = ref.cursor
      if (cursor.hasNext()) {
        val item = cursor.next()
        f(state, item).flatMap {
          case Right(s) => F.pure(s)
          case Left(s) =>
            state = s
            visit(ref)
        }
      } else {
        ref.rest.flatMap(this)
      }
    }

    def visit(ref: Suspend[F, A]): F[S] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[S] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(ref.rh)
      ref.lh.flatMap(this)
    }

    def visit(ref: Scope[F, A]): F[S] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[S] =
      f(state, ref.item).flatMap {
        case Right(s) => F.pure(s)
        case Left(s) =>
          stackPop() match {
            case null => F.pure(s)
            case xs =>
              state = s
              xs.flatMap(this)
          }
      }

    def visit(ref: Halt[F, A]): F[S] =
      ref.e match {
        case None =>
          stackPop() match {
            case null => F.pure(state)
            case xs => xs.flatMap(this)
          }
        case Some(e) =>
          F.raiseError(e)
      }

    def fail(e: Throwable): F[S] =
      F.raiseError(e)

    private def stackPop(): F[Iterant[F, A]] = {
      if (stack != null) stack.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }
  }
}