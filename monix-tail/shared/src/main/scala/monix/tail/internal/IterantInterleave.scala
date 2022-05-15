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

package monix.tail.internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

private[tail] object IterantInterleave {
  /**
    * Implementation for `Iterant.interleave`.
    */
  def apply[F[_], A](l: Iterant[F, A], r: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    Suspend(F.delay(new Loop().apply(l, r)))

  private final class Loop[F[_], A](implicit F: Sync[F]) extends ((Iterant[F, A], Iterant[F, A]) => Iterant[F, A]) {
    loop =>

    def apply(lh: Iterant[F, A], rh: Iterant[F, A]): Iterant[F, A] =
      lhLoop.visit(lh, rh)

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used by Concat:

    private[this] var _lhStack: ChunkedArrayStack[F[Iterant[F, A]]] = _
    private[this] var _rhStack: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def lhStackPush(ref: F[Iterant[F, A]]): Unit = {
      if (_lhStack == null) _lhStack = ChunkedArrayStack()
      _lhStack.push(ref)
    }

    private def lhStackPop(): F[Iterant[F, A]] =
      if (_lhStack == null) null.asInstanceOf[F[Iterant[F, A]]]
      else _lhStack.pop()

    private def rhStackPush(ref: F[Iterant[F, A]]): Unit = {
      if (_rhStack == null) _rhStack = ChunkedArrayStack()
      _rhStack.push(ref)
    }

    private def rhStackPop(): F[Iterant[F, A]] =
      if (_rhStack == null) null.asInstanceOf[F[Iterant[F, A]]]
      else _rhStack.pop()

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    private[this] val lhLoop = new LHLoop
    private[this] val rhLoop = new RHLoop

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    private final class LHLoop extends Iterant.Visitor[F, A, Iterant[F, A]] {
      protected var rhRef: F[Iterant[F, A]] = _

      def continue(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): F[Iterant[F, A]] = {
        rhRef = rh
        lh.map(this)
      }

      def continueRight(lhRest: F[Iterant[F, A]]) =
        rhLoop.continue(lhRest, rhRef)

      def visit(lh: Iterant[F, A], rh: Iterant[F, A]): Iterant[F, A] = {
        rhRef = F.pure(rh)
        this.apply(lh)
      }

      def visit(ref: Next[F, A]): Iterant[F, A] =
        Next(ref.item, continueRight(ref.rest))

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        visit(ref.toNextCursor())

      def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
        val cursor = ref.cursor
        if (cursor.hasNext()) {
          val item = cursor.next()
          val rest: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else ref.rest
          Next(item, continueRight(rest))
        } else {
          Suspend(ref.rest.map(this))
        }
      }

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        lhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        lhStackPop() match {
          case null =>
            Next(ref.item, continueRight(F.pure(Iterant.empty)))
          case xs =>
            Next(ref.item, continueRight(xs))
        }

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        ref.e match {
          case None =>
            lhStackPop() match {
              case null => ref
              case xs => Suspend(xs.map(this))
            }
          case _ =>
            ref
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    private final class RHLoop extends Iterant.Visitor[F, A, Iterant[F, A]] {
      protected var lhRef: F[Iterant[F, A]] = _

      def continue(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): F[Iterant[F, A]] = {
        lhRef = lh
        rh.map(this)
      }

      def continueLeft(rhRest: F[Iterant[F, A]]) =
        lhLoop.continue(lhRef, rhRest)

      def visit(ref: Next[F, A]): Iterant[F, A] =
        Next(ref.item, continueLeft(ref.rest))

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        visit(ref.toNextCursor())

      def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
        val cursor = ref.cursor
        if (cursor.hasNext()) {
          val item = cursor.next()
          val rest: F[Iterant[F, A]] = if (cursor.hasNext()) F.pure(ref) else ref.rest
          Next(item, continueLeft(rest))
        } else {
          Suspend(ref.rest.map(this))
        }
      }

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        rhStackPop() match {
          case null =>
            Next(ref.item, continueLeft(F.pure(Iterant.empty)))
          case xs =>
            Next(ref.item, continueLeft(xs))
        }

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref
              case xs => Suspend(xs.map(this))
            }
          case _ =>
            ref
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }
  }
}
