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

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.misc.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantOnError {
  /** Implementation for `Iterant.onErrorHandleWith`. */
  def handleWith[F[_], A](fa: Iterant[F, A], f: Throwable => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {
    def extractBatch(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    def sendError(stop: F[Unit], e: Throwable): Iterant[F, A] = {
      val next = stop.map { _ =>
        try f(e) catch {
          case NonFatal(err) =>
            Halt[F, A](Some(err))
        }
      }
      Suspend[F, A](next, stop)
    }

    def loop(fa: Iterant[F, A]): Iterant[F, A] =
      try fa match {
        case Next(a, lt, stop) =>
          Next(a, lt.map(loop), stop)

        case NextCursor(cursor, rest, stop) =>
          try {
            val array = extractBatch(cursor)
            val next = if (cursor.hasNext())
              F.pure(fa).map(loop)
            else
              rest.map(loop)

            if (array.length != 0)
              NextCursor(BatchCursor.fromAnyArray(array), next, stop)
            else
              Suspend(next, stop)
          } catch {
            case NonFatal(e) =>
              sendError(stop, e)
          }

        case NextBatch(batch, rest, stop) =>
          try {
            loop(NextCursor(batch.cursor(), rest, stop))
          } catch {
            case NonFatal(e) =>
              sendError(stop, e)
          }

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)
        case Last(_) | Halt(None) =>
          fa
        case Halt(Some(e)) =>
          f(e)
      } catch {
        case NonFatal(e) =>
          try f(e) catch { case NonFatal(err) => Halt(Some(err)) }
      }

    fa match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(F.delay(loop(fa)), fa.earlyStop)
      case _ =>
        loop(fa)
    }
  }

  /** Implementation for `Iterant.attempt`. */
  def attempt[F[_], A](fa: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, Either[Throwable, A]] = {
    def loop(fa: Iterant[F, A]): Iterant[F, Either[Throwable, A]] =
      fa match {
        case Next(a, rest, stop) =>
          Next(Right(a), rest.map(loop), stop)
        case NextBatch(batch, rest, stop) =>
          NextBatch(batch.map(Right.apply), rest.map(loop), stop)
        case NextCursor(batch, rest, stop) =>
          NextCursor(batch.map(Right.apply), rest.map(loop), stop)
        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)
        case Last(a) =>
          Last(Right(a))
        case Halt(None) =>
          fa.asInstanceOf[Iterant[F, Either[Throwable, A]]]
        case Halt(Some(ex)) =>
          Last(Left(ex))
      }

    fa match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(F.delay(loop(fa)), fa.earlyStop)
      case _ =>
        loop(fa)
    }
  }
}
