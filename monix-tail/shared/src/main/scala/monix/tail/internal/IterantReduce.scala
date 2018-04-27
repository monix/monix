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

import scala.runtime.ObjectRef

private[tail] object IterantReduce {
  /** Implementation for `Iterant.reduce`. */
  def apply[F[_], A](self: Iterant[F, A], op: (A, A) => A)
    (implicit F: Sync[F]): F[Option[A]] = {

    def loop(stopRef: ObjectRef[F[Unit]], state: A)(self: Iterant[F, A]): F[A] = {
      try self match {
        case Next(a, rest, stop) =>
          stopRef.elem = stop
          val newState = op(state, a)
          rest.flatMap(loop(stopRef, newState))
        case NextCursor(cursor, rest, stop) =>
          stopRef.elem = stop
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(stopRef, newState))
        case NextBatch(gen, rest, stop) =>
          stopRef.elem = stop
          val newState = gen.foldLeft(state)(op)
          rest.flatMap(loop(stopRef, newState))
        case Suspend(rest, stop) =>
          stopRef.elem = stop
          rest.flatMap(loop(stopRef, state))
        case Last(item) =>
          stopRef.elem = null.asInstanceOf[F[Unit]]
          F.pure(op(state, item))
        case Halt(None) =>
          F.pure(state)
        case Halt(Some(e)) =>
          stopRef.elem = null.asInstanceOf[F[Unit]]
          F.raiseError(e)
      } catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }

    def start(stopRef: ObjectRef[F[Unit]])(self: Iterant[F, A]): F[Option[A]] = {
      try self match {
        case Next(a, rest, stop) =>
          stopRef.elem = stop
          rest.flatMap(loop(stopRef, a)).map(Some.apply)

        case NextCursor(cursor, rest, stop) =>
          stopRef.elem = stop
          if (!cursor.hasNext())
            rest.flatMap(start(stopRef))
          else {
            val a = cursor.next()
            loop(stopRef, a)(self).map(Some.apply)
          }

        case NextBatch(batch, rest, stop) =>
          stopRef.elem = stop
          start(stopRef)(NextCursor(batch.cursor(), rest, stop))

        case Suspend(rest, stop) =>
          stopRef.elem = stop
          rest.flatMap(start(stopRef))

        case Last(a) =>
          stopRef.elem = null.asInstanceOf[F[Unit]]
          F.pure(Some(a))

        case Halt(opt) =>
          opt match {
            case None =>
              F.pure(None)
            case Some(e) =>
              stopRef.elem = null.asInstanceOf[F[Unit]]
              F.raiseError(e)
          }
      } catch {
        case e if NonFatal(e) =>
          F.raiseError(e)
      }
    }

    F.suspend{
      // Reference to keep track of latest `earlyStop` value
      val stopRef = ObjectRef.create(null.asInstanceOf[F[Unit]])
      // Catch-all exceptions, ensuring latest `earlyStop` gets called
      F.handleErrorWith(start(stopRef)(self)){ ex =>
        stopRef.elem match {
          case null => F.raiseError(ex)
          case stop => stop *> F.raiseError(ex)
        }
      }
    }
  }
}