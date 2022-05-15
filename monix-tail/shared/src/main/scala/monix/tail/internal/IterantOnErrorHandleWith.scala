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
import monix.execution.atomic.Atomic
import monix.execution.internal.Platform
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.{ Batch, BatchCursor }

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tail] object IterantOnErrorHandleWith {
  /**
    * Implementation for `Iterant.onErrorHandleWith`.
    */
  def apply[F[_], A](fa: Iterant[F, A], f: Throwable => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {

    Suspend(F.delay(new Loop(f).apply(fa)))
  }

  private final class Loop[F[_], A](handler: Throwable => Iterant[F, A])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] { self =>

    private[this] var wasErrorHandled = false

    private[this] val f = (e: Throwable) => {
      self.wasErrorHandled = true
      try handler(e)
      catch {
        case e2 if NonFatal(e) =>
          Iterant.raiseError[F, A](Platform.composeErrors(e, e2))
      }
    }

    def visit(ref: Next[F, A]): Iterant[F, A] =
      Next(ref.item, continueWith(ref.rest))

    def visit(ref: NextBatch[F, A]): Iterant[F, A] = {
      var handleError = true
      try {
        val cursor = ref.batch.cursor()
        handleError = false
        visit(NextCursor(cursor, ref.rest))
      } catch {
        case e if NonFatal(e) && handleError =>
          f(e)
      }
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      try {
        val array = extractFromCursor(ref.cursor)
        val next =
          if (ref.cursor.hasNext())
            F.pure(ref).map(this)
          else
            continueWith(ref.rest)

        if (array.length != 0)
          NextBatch(Batch.fromArray(array), next)
        else
          Suspend(next)
      } catch {
        case e if NonFatal(e) => f(e)
      }
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(continueWith(ref.rest))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      Concat(
        ref.lh.map(this),
        F.defer {
          if (self.wasErrorHandled)
            F.pure(Iterant.empty[F, A])
          else
            ref.rh.map(this)
        }
      )

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] = {
      val Scope(acquire, use, release) = ref

      Suspend(F.delay {
        val errors = Atomic(null: Throwable)

        val lh: Iterant[F, A] =
          Scope[F, Either[Throwable, S], A](
            acquire.attempt,
            es =>
              F.pure(es).flatMap {
                case Left(e) =>
                  pushError(errors, e)
                  F.pure(Iterant.empty)

                case Right(s) =>
                  try {
                    use(s).handleError { e =>
                      pushError(errors, e)
                      Iterant.empty
                    }.map(this)
                  } catch {
                    case NonFatal(e) =>
                      pushError(errors, e)
                      F.pure(Iterant.empty)
                  }
              },
            (es, exit) => {
              es match {
                case Left(_) => F.unit
                case Right(s) =>
                  try F.handleError(release(s, exit)) { e =>
                      pushError(errors, e)
                    }
                  catch {
                    case NonFatal(e) =>
                      F.delay(pushError(errors, e))
                  }
              }
            }
          )

        Concat(
          F.pure(lh),
          F.delay {
            val err = errors.getAndSet(null)
            if (err != null && !wasErrorHandled) {
              f(err)
            } else {
              Iterant.empty
            }
          }
        )
      })
    }

    def visit(ref: Last[F, A]): Iterant[F, A] =
      ref

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref.e match {
        case None => ref
        case Some(e) => f(e)
      }

    def fail(e: Throwable): Iterant[F, A] = {
      wasErrorHandled = true
      Iterant.raiseError(e)
    }

    private def continueWith(rest: F[Iterant[F, A]]): F[Iterant[F, A]] =
      rest.handleError(f).map(this)

    private def extractFromCursor(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    @tailrec
    private def pushError(ref: Atomic[Throwable], e: Throwable): Unit = {
      val current = ref.get()
      val update = current match {
        case null => e
        case e0 => Platform.composeErrors(e0, e)
      }
      if (!ref.compareAndSet(current, update))
        pushError(ref, e)
    }
  }
}
