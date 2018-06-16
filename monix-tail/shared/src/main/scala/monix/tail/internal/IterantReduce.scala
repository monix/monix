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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Resource, Suspend}
import scala.util.control.NonFatal


private[tail] object IterantReduce {
  /** Implementation for `Iterant.reduce`. */
  def apply[F[_], A](self: Iterant[F, A], op: (A, A) => A)
    (implicit F: Sync[F]): F[Option[A]] = {

    F.suspend { new Loop[F, A](op).start(self) }
  }

  private class Loop[F[_], A](op: (A, A) => A)(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[Option[A]]] {

    private[this] var state: A = _
    private[this] var stackRef: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): F[Option[A]] = {
      state = op(state, ref.item)
      ref.rest.flatMap(this)
    }

    def visit(ref: NextBatch[F, A]): F[Option[A]] = {
      state = ref.batch.foldLeft(state)(op)
      ref.rest.flatMap(this)
    }

    def visit(ref: NextCursor[F, A]): F[Option[A]] = {
      state = ref.cursor.foldLeft(state)(op)
      ref.rest.flatMap(this)
    }

    def visit(ref: Suspend[F, A]): F[Option[A]] =
      ref.rest.flatMap(this)

    def visit(ref: Concat[F, A]): F[Option[A]] = {
      stackPush(ref.rh)
      ref.lh.flatMap(this)
    }

    def visit[S](ref: Resource[F, S, A]): F[Option[A]] =
      ref.runFold(this)

    def visit(ref: Last[F, A]): F[Option[A]] =
      stackPop() match {
        case null => F.pure(Some(op(state, ref.item)))
        case some =>
          state = op(state, ref.item)
          some.flatMap(this)
      }

    def visit(ref: Halt[F, A]): F[Option[A]] =
      ref.e match {
        case None =>
          stackPop() match {
            case null => F.pure(Some(state))
            case some => some.flatMap(this)
          }
        case Some(e) =>
          F.raiseError(e)
      }

    def fail(e: Throwable): F[Option[A]] =
      F.raiseError(e)

    def stackPop(): F[Iterant[F, A]] =
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]

    def stackPush(fa: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = new ArrayStack()
      stackRef.push(fa)
    }

    val start: Iterant[F, A] => F[Option[A]] = { self =>
      try self match {
        case Next(a, rest) =>
          state = a
          rest.flatMap(this)

        case NextCursor(cursor, rest) =>
          if (!cursor.hasNext())
            rest.flatMap(start)
          else {
            state = cursor.next()
            this(self)
          }

        case NextBatch(batch, rest) =>
          start(NextCursor(batch.cursor(), rest))

        case Suspend(rest) =>
          rest.flatMap(start)

        case s @ Resource(_, _, _) =>
          s.runFold(start)

        case Concat(lh, rh) =>
          stackPush(rh)
          lh.flatMap(start)

        case Last(a) =>
          stackPop() match {
            case null => F.pure(Some(a))
            case some =>
              state = a
              some.flatMap(this)
          }

        case Halt(opt) =>
          opt match {
            case Some(e) => F.raiseError(e)
            case None =>
              stackPop() match {
                case null => F.pure(None)
                case next => next.flatMap(start)
              }
          }
      } catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }
  }
}