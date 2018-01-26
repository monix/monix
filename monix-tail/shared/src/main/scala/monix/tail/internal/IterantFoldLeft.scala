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
import monix.execution.misc.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}

import scala.collection.mutable
import scala.runtime.ObjectRef

private[tail] object IterantFoldLeft {
  /**
    * Implementation for `Iterant#foldLeftL`
    */
  final def apply[F[_], S, A](source: Iterant[F, A], seed: => S)(op: (S,A) => S)
                             (implicit F: Sync[F]): F[S] = {

    def loop(stopRef: ObjectRef[F[Unit]])(self: Iterant[F, A], state: S): F[S] = {
      try self match {
        case Next(a, rest, stop) =>
          stopRef.elem = stop
          val newState = op(state, a)
          rest.flatMap(loop(stopRef)(_, newState))
        case NextCursor(cursor, rest, stop) =>
          stopRef.elem = stop
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(stopRef)(_, newState))
        case NextBatch(gen, rest, stop) =>
          stopRef.elem = stop
          val newState = gen.foldLeft(state)(op)
          rest.flatMap(loop(stopRef)(_, newState))
        case Suspend(rest, stop) =>
          stopRef.elem = stop
          rest.flatMap(loop(stopRef)(_, state))
        case Last(item) =>
          F.pure(op(state,item))
        case Halt(None) =>
          F.pure(state)
        case Halt(Some(ex)) =>
          stopRef.elem = null.asInstanceOf[F[Unit]]
          F.raiseError(ex)
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
        // Reference to keep track of latest `earlyStop` value
        val stopRef = ObjectRef.create(null.asInstanceOf[F[Unit]])
        // Catch-all exceptions, ensuring latest `earlyStop` gets called
        F.handleErrorWith(loop(stopRef)(source, init)) { ex =>
          stopRef.elem match {
            case null => F.raiseError(ex)
            case stop => stop *> F.raiseError(ex)
          }
        }
      } catch {
        case NonFatal(e) if catchErrors =>
          source.earlyStop *> F.raiseError(e)
      }
    }
  }

  /**
    * Implementation for `Iterant#toListL`
    */
  def toListL[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): F[List[A]] = {
    val buffer = IterantFoldLeft(source, mutable.ListBuffer.empty[A])((acc, a) => acc += a)
    buffer.map(_.toList)
  }
}