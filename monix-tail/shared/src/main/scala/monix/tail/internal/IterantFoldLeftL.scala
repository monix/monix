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
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

import scala.collection.mutable
import scala.util.control.NonFatal

private[tail] object IterantFoldLeftL {
  /**
    * Implementation for `Iterant#foldLeftL`
    */
  final def apply[F[_], S, A](source: Iterant[F, A], seed: => S)(op: (S, A) => S)(implicit F: Sync[F]): F[S] = {

    F.defer {
      var catchErrors = true
      try {
        // handle exception in the seed
        val init = seed
        catchErrors = false
        new Loop(init, op).apply(source)
      } catch {
        case e if NonFatal(e) && catchErrors =>
          F.raiseError(e)
      }
    }
  }

  /**
    * Implementation for `Iterant#toListL`
    */
  def toListL[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[List[A]] = {
    IterantFoldLeftL(source, mutable.ListBuffer.empty[A])((acc, a) => acc += a)
      .map(_.toList)
  }

  private final class Loop[F[_], S, A](seed: S, op: (S, A) => S)(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[S]] { loop =>

    /** Current calculated state. */
    private[this] var state = seed

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

    private[this] val concatContinue: (S => F[S]) =
      state =>
        stackPop() match {
          case null => F.pure(state)
          case xs => xs.flatMap(loop)
        }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Next[F, A]): F[S] = {
      state = op(state, ref.item)
      ref.rest.flatMap(loop)
    }

    def visit(ref: NextBatch[F, A]): F[S] = {
      state = ref.batch.foldLeft(state)(op)
      ref.rest.flatMap(loop)
    }

    def visit(ref: NextCursor[F, A]): F[S] = {
      state = ref.cursor.foldLeft(state)(op)
      ref.rest.flatMap(loop)
    }

    def visit(ref: Suspend[F, A]): F[S] =
      ref.rest.flatMap(loop)

    def visit(ref: Concat[F, A]): F[S] = {
      stackPush(ref.rh)
      ref.lh.flatMap(loop).flatMap(concatContinue)
    }

    def visit[R](ref: Scope[F, R, A]): F[S] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[S] = {
      state = op(state, ref.item)
      F.pure(state)
    }

    def visit(ref: Halt[F, A]): F[S] =
      ref.e match {
        case None => F.pure(state)
        case Some(e) => F.raiseError(e)
      }

    def fail(e: Throwable): F[S] =
      F.raiseError(e)
  }
}
