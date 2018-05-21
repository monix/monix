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

import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.Constants.canceledRef
import monix.tail.internal.IterantUtils._
import cats.syntax.all._
import cats.effect.{ExitCase, Sync}
import monix.execution.internal.Platform
import scala.util.control.NonFatal
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantExitCallback {
  /**
    * Implementation for `Iterant#doOnEarlyStop`
    */
  def doOnEarlyStop[F[_], A](source: Iterant[F, A], f: F[Unit])
    (implicit F: Sync[F]): Iterant[F, A] = {

    source match {
      case Next(head, rest, stop) =>
        Next(head, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextCursor(items, rest, stop) =>
        NextCursor(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextBatch(items, rest, stop) =>
        NextBatch(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case ref@(Halt(_) | Last(_)) =>
        ref // nothing to do
    }
  }

  /**
    * Implementation for `Iterant#doOnExitCase`
    */
  def doOnExitCase[F[_], A](fa: Iterant[F, A], f: ExitCase[Throwable] => F[Unit])
    (implicit F: Sync[F]): Iterant[F, A] = {

    def extractBatch(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    def signalAndSendError(stop: F[Unit], e1: Throwable): F[Iterant[F, A]] = {
      stop.attempt.flatMap { result =>
        val err1 = Platform.composeErrors(e1, result)
        try {
          f(ExitCase.Error(err1)).attempt.map { r =>
            Halt(Some(Platform.composeErrors(err1, r)))
          }
        } catch {
          case err2 if NonFatal(err2) =>
            // In case `f` throws, we must still send the original error
            F.pure(Halt[F, A](Some(Platform.composeErrors(err1, err2))))
        }
      }
    }

    def tailGuard(ffa: F[Iterant[F, A]], stop: F[Unit]) =
      ffa.handleErrorWith(signalAndSendError(stop, _))

    def loop(fa: Iterant[F, A]): Iterant[F, A] = {
      try fa match {
        case Next(a, lt, stop) =>
          val rest = tailGuard(lt, stop).map(loop)
          Next(a, rest, stop.flatMap(_ => f(canceledRef)))

        case NextCursor(cursor, rest, stop) =>
          val stopE = stop.flatMap(_ => f(canceledRef))
          try {
            val array = extractBatch(cursor)
            val next =
              if (cursor.hasNext())
                F.delay(loop(fa))
              else
                tailGuard(rest, stop).map(loop)

            array.length match {
              case 1 =>
                Next(array(0), next, stopE)
              case 0 =>
                Suspend(next, stopE)
              case _ =>
                NextCursor(BatchCursor.fromArray(array), next, stopE)
            }
          } catch {
            case e if NonFatal(e) =>
              Suspend(signalAndSendError(stop, e), stopE)
          }

        case NextBatch(batch, rest, stop) =>
          val stopE = stop.flatMap(_ => f(canceledRef))
          try {
            loop(NextCursor(batch.cursor(), rest, stopE))
          } catch {
            case e if NonFatal(e) =>
              Suspend(signalAndSendError(stop, e), stopE)
          }

        case Suspend(rest, stop) =>
          val fa = tailGuard(rest, stop).map(loop)
          Suspend(fa, stop.flatMap(_ => f(canceledRef)))

        case Halt(Some(e)) =>
          // In case `f` throws, we must still throw the original error
          try
            Suspend(f(ExitCase.Error(e)).attempt.map(_ => fa), F.suspend(f(canceledRef)))
          catch {
            case err if NonFatal(err) =>
              Halt(Some(Platform.composeErrors(e, err)))
          }

        case /* Last(_) | Halt(None) */ _ =>
          Suspend(
            try {
              f(ExitCase.Completed).attempt.map {
                case Left(e) => Halt(Some(e))
                case _ => fa
              }
            } catch {
              case NonFatal(e) =>
                F.pure(Halt(Some(e)))
            },
            F.suspend(f(canceledRef))
          )

      } catch {
        case e if NonFatal(e) =>
          // Error happened due to calling the exit-case function, so we cannot
          // call it here again, because it is broken
          signalError(fa, e)
      }
    }

    Suspend(F.delay(loop(fa)), fa.earlyStop.flatMap(_ => f(canceledRef)))
  }
}
