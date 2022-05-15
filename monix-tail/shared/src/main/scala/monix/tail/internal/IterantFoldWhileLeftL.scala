/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantFoldWhileLeftL {
  /**
    * Implementation for `Iterant.foldWhileLeftL`.
    */
  def strict[F[_], A, S](self: Iterant[F, A], seed: => S, f: (S, A) => Either[S, S])(implicit F: Sync[F]): F[S] = {

    F.delay(seed).flatMap { state =>
      new StrictLoop(state, f).apply(self).map {
        case Right(r) => r
        case Left(l) => l
      }
    }
  }

  /**
    * Implementation for `Iterant.foldWhileLeftEvalL`.
    */
  def eval[F[_], A, S](self: Iterant[F, A], seed: F[S], f: (S, A) => F[Either[S, S]])(implicit F: Sync[F]): F[S] = {

    seed.flatMap { state =>
      new LazyLoop(state, f).apply(self).map {
        case Left(l) => l
        case Right(r) => r
      }
    }
  }

  private class StrictLoop[F[_], A, S](seed: S, f: (S, A) => Either[S, S])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[Either[S, S]]] { self =>

    private[this] var state: S = seed

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used in visit(Concat)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    private[this] val concatContinue: (Either[S, S] => F[Either[S, S]]) = {
      case left @ Left(_) =>
        stackPop() match {
          case null => F.pure(left)
          case xs => xs.flatMap(self)
        }
      case right =>
        F.pure(right)
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[Either[S, S]] =
      f(state, ref.item) match {
        case Left(s) =>
          state = s
          ref.rest.flatMap(this)
        case value @ Right(s) =>
          state = s
          F.pure(value)
      }

    def visit(ref: NextBatch[F, A]): F[Either[S, S]] =
      process(ref.batch.cursor(), ref.rest)

    def visit(ref: NextCursor[F, A]): F[Either[S, S]] =
      process(ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): F[Either[S, S]] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Either[S, S]] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this).flatMap(concatContinue)
    }

    def visit[R](ref: Scope[F, R, A]): F[Either[S, S]] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Either[S, S]] =
      f(state, ref.item) match {
        case left @ Left(s) =>
          state = s
          F.pure(left)
        case right @ Right(s) =>
          state = s
          F.pure(right)
      }

    def visit(ref: Halt[F, A]): F[Either[S, S]] =
      ref.e match {
        case None => F.pure(Left(state))
        case Some(e) => F.raiseError(e)
      }

    def fail(e: Throwable): F[Either[S, S]] =
      F.raiseError(e)

    def process(cursor: BatchCursor[A], rest: F[Iterant[F, A]]): F[Either[S, S]] = {
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

      if (hasResult) {
        F.pure(Right(state))
      } else {
        rest.flatMap(this)
      }
    }
  }

  private class LazyLoop[F[_], A, S](seed: S, f: (S, A) => F[Either[S, S]])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[Either[S, S]]] { self =>

    private[this] var state: S = seed

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used in visit(Concat)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    private[this] val concatContinue: (Either[S, S] => F[Either[S, S]]) = {
      case left @ Left(_) =>
        stackPop() match {
          case null => F.pure(left)
          case xs => xs.flatMap(self)
        }
      case right =>
        F.pure(right)
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[Either[S, S]] =
      f(state, ref.item).flatMap {
        case Left(s) =>
          state = s
          ref.rest.flatMap(this)
        case right @ Right(s) =>
          state = s
          F.pure(right)
      }

    def visit(ref: NextBatch[F, A]): F[Either[S, S]] =
      visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): F[Either[S, S]] = {
      val cursor = ref.cursor
      if (!cursor.hasNext()) {
        ref.rest.flatMap(this)
      } else {
        f(state, cursor.next()).flatMap {
          case Left(l) =>
            state = l
            F.pure(ref).flatMap(this)
          case value @ Right(r) =>
            state = r
            F.pure(value)
        }
      }
    }

    def visit(ref: Suspend[F, A]): F[Either[S, S]] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Either[S, S]] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this).flatMap(concatContinue)
    }

    def visit[R](ref: Scope[F, R, A]): F[Either[S, S]] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Either[S, S]] =
      f(state, ref.item).flatMap {
        case left @ Left(l) =>
          state = l
          F.pure(left)
        case right @ Right(r) =>
          state = r
          F.pure(right)
      }

    def visit(ref: Halt[F, A]): F[Either[S, S]] =
      ref.e match {
        case None => F.pure(Left(state))
        case Some(e) => F.raiseError(e)
      }

    def fail(e: Throwable): F[Either[S, S]] =
      F.raiseError(e)
  }
}
