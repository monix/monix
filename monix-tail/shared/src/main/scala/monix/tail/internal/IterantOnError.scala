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
import monix.execution.internal.Platform
import scala.util.control.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.{Batch, BatchCursor}
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

    def sendError(stop: F[Unit], e1: Throwable): Iterant[F, A] = {
      val next = stop.attempt.map { result =>
        val err1 = result match {
          case Left(e2) => Platform.composeErrors(e1, e2)
          case _ => e1
        }
        try f(err1) catch {
          case err2 if NonFatal(err2) =>
            Halt[F, A](Some(Platform.composeErrors(err1, err2)))
        }
      }
      Suspend[F, A](next, stop)
    }

    def tailGuard(ffa: F[Iterant[F, A]], stop: F[Unit]) =
      ffa.handleError(sendError(stop, _))

    def loop(fa: Iterant[F, A]): Iterant[F, A] =
      try fa match {
        case Next(a, lt, stop) =>
          Next(a, tailGuard(lt, stop).map(loop), stop)

        case NextCursor(cursor, rest, stop) =>
          try {
            val array = extractBatch(cursor)
            val next =
              if (cursor.hasNext()) F.delay(loop(fa))
              else tailGuard(rest, stop).map(loop)

            if (array.length != 0)
              NextCursor(BatchCursor.fromArray(array), next, stop)
            else
              Suspend(next, stop)
          } catch {
            case e if NonFatal(e) =>
              sendError(stop, e)
          }

        case NextBatch(batch, rest, stop) =>
          try {
            loop(NextCursor(batch.cursor(), rest, stop))
          } catch {
            case e if NonFatal(e) =>
              sendError(stop, e)
          }

        case Suspend(rest, stop) =>
          Suspend(tailGuard(rest, stop).map(loop), stop)
        case Last(_) | Halt(None) =>
          fa
        case Halt(Some(e)) =>
          f(e)
      } catch {
        case e if NonFatal(e) =>
          try f(e) catch { case err if NonFatal(err) => Halt(Some(err)) }
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
    type Attempt = Either[Throwable, A]

    def tailGuard(ffa: F[Iterant[F, Attempt]], stop: F[Unit]) =
      ffa.handleErrorWith { ex =>
        val end = Iterant.lastS[F, Attempt](Left(ex)).pure[F]
        // this will swallow exception if earlyStop fails
        stop.attempt *> end
      }

    def extractBatch(ref: BatchCursor[A]): Array[Attempt] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[Attempt]
      while (size > 0 && ref.hasNext()) {
        try {
          buffer += Right(ref.next())
          size -= 1
        } catch {
          case NonFatal(ex) =>
            buffer += Left(ex)
            size = 0
        }
      }
      buffer.toArray[Attempt]
    }

    def loop(fa: Iterant[F, A]): Iterant[F, Attempt] =
      fa match {
        case Next(a, rest, stop) =>
          Next(Right(a), tailGuard(rest.map(loop), stop), stop)
        case NextBatch(batch, rest, stop) =>
          loop(NextCursor(batch.cursor(), rest, stop))
        case NextCursor(cursor, rest, stop) =>
          val cb = extractBatch(cursor)
          val batch = Batch.fromArray(cb)
          if (cb.length > 0 && cb.last.isLeft) {
            NextBatch(batch, F.pure(Iterant.empty), stop)
          } else if (!cursor.hasNext()) {
            NextBatch(batch, tailGuard(rest.map(loop), stop), stop)
          } else {
            NextBatch(batch, tailGuard(F.delay(loop(fa)), stop), stop)
          }
        case Suspend(rest, stop) =>
          Suspend(tailGuard(rest.map(loop), stop), stop)
        case Last(a) =>
          Last(Right(a))
        case Halt(None) =>
          fa.asInstanceOf[Iterant[F, Attempt]]
        case Halt(Some(ex)) =>
          Last(Left(ex))
      }

    fa match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(tailGuard(F.delay(loop(fa)), fa.earlyStop), fa.earlyStop)
      case _ =>
        loop(fa)
    }
  }
}
