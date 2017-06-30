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

package monix.tail.internal

import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import scala.collection.mutable
import scala.util.control.NonFatal

private[tail] object IterantFoldLeftL {
  /**
    * Implementation for `Iterant#foldLeftL`
    */
  final def apply[F[_], S, A](source: Iterant[F, A], seed: => S)(op: (S,A) => S)
    (implicit F: Sync[F]): F[S] = {

    def loop(self: Iterant[F, A], state: S): F[S] = {
      try self match {
        case Next(a, rest, _) =>
          val newState = op(state, a)
          rest.flatMap(loop(_, newState))
        case NextCursor(cursor, rest, _) =>
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case NextBatch(gen, rest, _) =>
          val newState = gen.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case Suspend(rest, _) =>
          rest.flatMap(loop(_, state))
        case Last(item) =>
          F.pure(op(state,item))
        case Halt(None) =>
          F.pure(state)
        case Halt(Some(ex)) =>
          F.raiseError(ex)
      } catch {
        case NonFatal(ex) =>
          source.earlyStop.map(_ => throw ex)
      }
    }

    F.suspend {
      var catchErrors = true
      try {
        val init = seed
        catchErrors = false
        loop(source, init)
      } catch {
        case NonFatal(ex) if catchErrors =>
          source.earlyStop.map(_ => throw ex)
      }
    }
  }

  /**
    * Implementation for `Iterant#toListL`
    */
  def toListL[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[List[A]] = {
    val buffer = IterantFoldLeftL(source, mutable.ListBuffer.empty[A])((acc, a) => acc += a)
    buffer.map(_.toList)
  }
}