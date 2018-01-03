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
import monix.execution.misc.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

private[tail] object IterantReduce {
  /** Implementation for `Iterant.reduce`. */
  def apply[F[_], A](self: Iterant[F, A], op: (A, A) => A)
    (implicit F: Sync[F]): F[Option[A]] = {

    def loop(state: A)(self: Iterant[F, A]): F[A] = {
      try self match {
        case Next(a, rest, _) =>
          val newState = op(state, a)
          rest.flatMap(loop(newState))
        case NextCursor(cursor, rest, _) =>
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(newState))
        case NextBatch(gen, rest, _) =>
          val newState = gen.foldLeft(state)(op)
          rest.flatMap(loop(newState))
        case Suspend(rest, _) =>
          rest.flatMap(loop(state))
        case Last(item) =>
          F.pure(op(state,item))
        case Halt(None) =>
          F.pure(state)
        case Halt(Some(e)) =>
          F.raiseError(e)
      } catch {
        case NonFatal(e) =>
          self.earlyStop *> F.raiseError(e)
      }
    }

    def start(self: Iterant[F, A]): F[Option[A]] = {
      try self match {
        case Next(a, rest, _) =>
          rest.flatMap(loop(a)).map(Some.apply)

        case NextCursor(cursor, rest, _) =>
          if (!cursor.hasNext())
            rest.flatMap(start)
          else {
            val a = cursor.next()
            loop(a)(self).map(Some.apply)
          }

        case NextBatch(batch, rest, stop) =>
          start(NextCursor(batch.cursor(), rest, stop))

        case Suspend(rest, _) =>
          rest.flatMap(start)

        case Last(a) =>
          F.pure(Some(a))

        case Halt(opt) =>
          opt match {
            case None => F.pure(None)
            case Some(e) => F.raiseError(e)
          }
      } catch {
        case NonFatal(e) =>
          self.earlyStop *> F.raiseError(e)
      }
    }

    F.suspend(start(self))
  }
}
