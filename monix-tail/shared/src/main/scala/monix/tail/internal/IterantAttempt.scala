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
import monix.execution.atomic.Atomic
import monix.execution.internal.Platform
import monix.execution.UncaughtExceptionReporter.{default => Logger}
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{Batch, BatchCursor}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tail] object IterantAttempt {
  /**
    * Implementation for `Iterant.attempt`.
    */
  def apply[F[_], A](fa: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, Either[Throwable, A]] = {
    // Suspending execution in order to preserve laziness and
    // referential transparency
    Suspend(F.delay(new AttemptVisitor[F, A].apply(fa)))
  }

  private final class AttemptVisitor[F[_], A](implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, Either[Throwable, A]]] {
    self =>

    type Attempt = Either[Throwable, A]

    private[this] var wasErrorHandled = false
    private[this] val handleError = (e: Throwable) => {
      self.wasErrorHandled = true
      Left(e): Attempt
    }

    def visit(ref: Next[F, A]): Iterant[F, Either[Throwable, A]] =
      Next(Right(ref.item), continueWith(ref.rest))

    def visit(ref: NextBatch[F, A]): Iterant[F, Either[Throwable, A]] = {
      val NextBatch(batch, rest) = ref
      var signalError = true
      try {
        val cursor = batch.cursor()
        signalError = false
        handleCursor(NextCursor(cursor, rest), cursor, rest)
      } catch {
        case e if NonFatal(e) && signalError =>
          Iterant.now(self.handleError(e))
      }
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, Either[Throwable, A]] =
      handleCursor(ref, ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): Iterant[F, Either[Throwable, A]] =
      Suspend(continueWith(ref.rest))

    def visit(ref: Concat[F, A]): Iterant[F, Either[Throwable, A]] =
      Concat(
        ref.lh.map(this),
        F.defer {
          if (self.wasErrorHandled)
            F.pure(Iterant.empty[F, Attempt])
          else
            ref.rh.map(this)
        })

    def visit[S](ref: Scope[F, S, A]): Iterant[F, Attempt] = {
      val Scope(acquire, use, release) = ref

      Suspend(F.delay {
        val errors = Atomic(null: Throwable)

        val lh: Iterant[F, Attempt] =
          Scope[F, Either[Throwable, S], Attempt](
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
                  try
                    F.handleError(release(s, exit)) { e =>
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
            if (err != null) {
              if (!wasErrorHandled)
                Last(handleError(err))
              else {
                Logger.reportFailure(err)
                Iterant.empty
              }
            } else {
              Iterant.empty
            }
          }
        )
      })
    }

    def visit(ref: Last[F, A]): Iterant[F, Either[Throwable, A]] =
      Last(Right(ref.item))

    def visit(ref: Halt[F, A]): Iterant[F, Either[Throwable, A]] =
      ref.e match {
        case None => ref.asInstanceOf[Iterant[F, Attempt]]
        case Some(error) => Last(handleError(error))
      }

    def fail(e: Throwable): Iterant[F, Either[Throwable, A]] =
      Iterant.raiseError(e)

    private[this] val continueMapRef: Either[Throwable, Iterant[F, A]] => Iterant[F, Attempt] = {
      case Left(e) =>
        Iterant.now(handleError(e))
      case Right(iter) =>
        self(iter)
    }

    private def continueWith(rest: F[Iterant[F, A]]): F[Iterant[F, Attempt]] =
      rest.attempt.map(continueMapRef)

    private def handleCursor(
      node: NextCursor[F, A],
      cursor: BatchCursor[A],
      rest: F[Iterant[F, A]]): Iterant[F, Attempt] = {

      try {
        val array = extractFromCursor(cursor)
        val next =
          if (cursor.hasNext()) F.delay(self(node))
          else continueWith(rest)

        if (array.length != 0)
          NextBatch(Batch.fromArray(array), next)
        else
          Suspend(next)
      } catch {
        case e if NonFatal(e) => Iterant.pure(handleError(e))
      }
    }

    private def extractFromCursor(ref: BatchCursor[A]): Array[Attempt] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[Attempt]
      while (size > 0 && ref.hasNext()) {
        buffer += Right(ref.next())
        size -= 1
      }
      buffer.toArray[Attempt]
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
