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
import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{Batch, BatchCursor}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tail] object IterantAttempt {
  /**
    * Implementation for `Iterant.attempt`.
    */
  def apply[F[_], A](fa: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, Either[Throwable, A]] = {
    val loop = new AttemptVisitor[F, A]
    fa match {
      case NextBatch(_, _) | NextCursor(_, _) | Concat(_, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(F.delay(loop(fa)))
      case _ =>
        loop(fa)
    }
  }

  private final class AttemptVisitor[F[_], A](implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Either[Throwable, A]] {
    loop =>

    type Attempt = Either[Throwable, A]
    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): Iterant[F, Either[Throwable, A]] =
      Next(Right(ref.item), continueWith(ref.rest))

    def visit(ref: NextBatch[F, A]): Iterant[F, Either[Throwable, A]] = {
      val NextBatch(batch, rest) = ref
      var handleError = true
      try {
        val cursor = batch.cursor()
        handleError = false
        handleCursor(NextCursor(cursor, rest), cursor, rest)
      } catch {
        case e if NonFatal(e) && handleError =>
          Iterant.now(Left(e))
      }
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, Either[Throwable, A]] =
      handleCursor(ref, ref.cursor, ref.rest)

    def visit(ref: Suspend[F, A]): Iterant[F, Either[Throwable, A]] =
      Suspend(continueWith(ref.rest))

    def visit(ref: Concat[F, A]): Iterant[F, Either[Throwable, A]] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(ref.rh)
      Suspend(continueWith(ref.lh))
    }

    def visit(ref: Scope[F, A]): Iterant[F, Either[Throwable, A]] = {
      val Scope(open, use, close) = ref
      Suspend {
        open.attempt.map {
          case Right(_) =>
            var thrownRef: Throwable = null
            val lh: Iterant[F, Attempt] =
              Scope(F.unit, continueWith(use), exit =>
                F.suspend(F.handleError(close(exit)) { e =>
                  thrownRef = e
                }))

            Concat(F.pure(lh), F.delay {
              if (thrownRef == null) Iterant.empty
              else Last(Left(thrownRef))
            })

          case left@Left(_) =>
            Last(left.asInstanceOf[Attempt])
        }
      }
    }

    def visit(ref: Last[F, A]): Iterant[F, Either[Throwable, A]] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => Last(Right(ref.item))
        case stream => Next(Right(ref.item), continueWith(stream))
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, Either[Throwable, A]] =
      ref.e match {
        case None =>
          val next =
            if (stack != null) stack.pop()
            else null.asInstanceOf[F[Iterant[F, A]]]

          next match {
            case null => ref.asInstanceOf[Iterant[F, Attempt]]
            case stream => Suspend(continueWith(stream))
          }
        case Some(error) =>
          Last(Left(error))
      }

    private def continueWith(rest: F[Iterant[F, A]]): F[Iterant[F, Attempt]] =
      rest.attempt.map {
        case error@Left(_) =>
          Iterant.now(error.asInstanceOf[Attempt])
        case Right(iter) =>
          loop(iter)
      }

    private def handleCursor(
      node: NextCursor[F, A],
      cursor: BatchCursor[A],
      rest: F[Iterant[F, A]]): Iterant[F, Attempt] = {

      try {
        val array = extractFromCursor(cursor)
        val next =
          if (cursor.hasNext()) F.delay(loop(node))
          else continueWith(rest)

        if (array.length != 0)
          NextBatch(Batch.fromArray(array), next)
        else
          Suspend(next)
      } catch {
        case e if NonFatal(e) => Iterant.pure(Left(e))
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
  }
}
