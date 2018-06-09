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

import cats.syntax.all._
import cats.effect.Sync
import scala.util.control.NonFatal

import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}


private[tail] object IterantReduce {
  /** Implementation for `Iterant.reduce`. */
  def apply[F[_], A](self: Iterant[F, A], op: (A, A) => A)
    (implicit F: Sync[F]): F[Option[A]] = {

    F.suspend { new Loop[F, A](op).start(self) }
  }

  private class Loop[F[_], A](op: (A, A) => A)(implicit F: Sync[F])
    extends (Iterant[F, A] => F[Option[A]]) {
    private[this] var state: A = _
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()

    def apply(self: Iterant[F, A]): F[Option[A]] = {
      try self match {
        case Next(a, rest) =>
          state = op(state, a)
          rest.flatMap(this)
        case NextCursor(cursor, rest) =>
          state = cursor.foldLeft(state)(op)
          rest.flatMap(this)
        case NextBatch(gen, rest) =>
          state = gen.foldLeft(state)(op)
          rest.flatMap(this)
        case Suspend(rest) =>
          rest.flatMap(this)
        case s @ Scope(_, _, _) =>
          s.runFold(this)
        case Concat(lh, rh) =>
          stack.push(rh)
          lh.flatMap(this)
        case Last(item) =>
          stack.pop() match {
            case null => F.pure(Some(op(state, item)))
            case some =>
              state = op(state, item)
              some.flatMap(this)
          }
        case Halt(None) =>
          stack.pop() match {
            case null => F.pure(Some(state))
            case some => some.flatMap(this)
          }
        case Halt(Some(e)) =>
          F.raiseError(e)
      } catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
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

        case s @ Scope(_, _, _) =>
          s.runFold(start)

        case Concat(lh, rh) =>
          stack.push(rh)
          lh.flatMap(start)

        case Last(a) =>
          stack.pop() match {
            case null => F.pure(Some(a))
            case some =>
              state = a
              some.flatMap(this)
          }

        case Halt(opt) =>
          val next = stack.pop()
          opt match {
            case Some(e) =>
              F.raiseError(e)
            case None if next == null =>
              F.pure(None)
            case None =>
              next.flatMap(start)
          }
      } catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }
  }
}