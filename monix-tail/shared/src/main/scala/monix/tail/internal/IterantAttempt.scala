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

import cats.effect.{ExitCase, Sync}
import cats.syntax.all._
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
    val loop = new AttemptLoop[F, A]
    Suspend(F.delay(loop(fa)).handleError(ex => Last(Left(ex))))
  }

  /**
    * Describing the loop as a class because we can better control
    * memory allocations this way.
    */
  private final class AttemptLoop[F[_], A](implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, Either[Throwable, A]]) {
    loop =>

    type Attempt = Either[Throwable, A]

    def apply(node: Iterant[F, A]): Iterant[F, Attempt] =
      node match {
        case Next(a, rest) =>
          Next(Right(a), continueWith(rest))
        case NextBatch(batch, rest) =>
          handleBatch(batch, rest)
        case node@NextCursor(cursor, rest) =>
          handleCursor(node, cursor, rest)
        case Suspend(rest) =>
          Suspend(continueWith(rest))
        case Last(a) =>
          Last(Right(a))
        case Halt(None) =>
          node.asInstanceOf[Iterant[F, Attempt]]
        case Halt(Some(ex)) =>
          Last(Left(ex))
        case node@Concat(_, _) =>
          node.runMap(loop)
        case Scope(open, use, close) =>
          handleScope(open, use, close)
      }

    def continueWith(rest: F[Iterant[F, A]]): F[Iterant[F, Attempt]] =
      rest.attempt.map {
        case error@Left(_) =>
          Iterant.now(error.asInstanceOf[Attempt])
        case Right(iter) =>
          loop(iter)
      }

    def handleBatch(batch: Batch[A], rest: F[Iterant[F, A]]): Iterant[F, Attempt] = {
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

    def handleCursor(node: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]): Iterant[F, Attempt] = {
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

    def extractFromCursor(ref: BatchCursor[A]): Array[Attempt] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[Attempt]
      while (size > 0 && ref.hasNext()) {
        buffer += Right(ref.next())
        size -= 1
      }
      buffer.toArray[Attempt]
    }

    def handleScope(open: F[Unit], rest: F[Iterant[F, A]], close: ExitCase[Throwable] => F[Unit]): Iterant[F, Attempt] =
      Suspend {
        open.attempt.map {
          case Right(_) =>
            var thrownRef: Throwable = null
            val lh: Iterant[F, Attempt] =
              Scope(F.unit, continueWith(rest), exit =>
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
}
