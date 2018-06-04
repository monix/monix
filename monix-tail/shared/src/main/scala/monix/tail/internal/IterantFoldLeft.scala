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

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}

import scala.collection.mutable

private[tail] object IterantFoldLeft {
  /**
    * Implementation for `Iterant#foldLeftL`
    */
  final def apply[F[_], S, A](source: Iterant[F, A], seed: => S)(op: (S,A) => S)
    (implicit F: Sync[F]): F[S] = {

    def continueWith(state: S, stack: List[F[Iterant[F, A]]]): F[S] =
      stack match {
        case Nil => F.pure(state)
        case x :: xs => x.flatMap(loop(state, xs))
      }

    def loop(state: S, stack: List[F[Iterant[F, A]]])(self: Iterant[F, A]): F[S] = {
      try self match {
        case Next(a, rest) =>
          val newState = op(state, a)
          rest.flatMap(loop(newState, stack))

        case NextCursor(cursor, rest) =>
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(newState, stack))

        case NextBatch(gen, rest) =>
          val newState = gen.foldLeft(state)(op)
          rest.flatMap(loop(newState, stack))

        case Suspend(rest) =>
          rest.flatMap(loop(state, stack))

        case Last(item) =>
          continueWith(op(state, item), stack)

        case Halt(None) =>
          continueWith(state, stack)

        case Halt(Some(ex)) =>
          F.raiseError(ex)

        case b @ Scope(_, _, _) =>
          b.runFold(loop(state, stack))

        case Concat(lh, rh) =>
          lh.flatMap(loop(state, rh :: stack))

      } catch {
        case ex if NonFatal(ex) =>
          F.raiseError(ex)
      }
    }

    F.suspend {
      var catchErrors = true
      try {
        // handle exception in the seed
        val init = seed
        catchErrors = false
        loop(init, Nil)(source)
      } catch {
        case NonFatal(e) if catchErrors =>
          F.raiseError(e)
      }
    }
  }

  /**
    * Implementation for `Iterant#toListL`
    */
  def toListL[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[List[A]] = {
    IterantFoldLeft(source, mutable.ListBuffer.empty[A])((acc, a) => acc += a)
      .map(_.toList)
  }
}