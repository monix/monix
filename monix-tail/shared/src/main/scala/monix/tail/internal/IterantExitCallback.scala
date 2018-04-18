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
import monix.tail.internal.IterantUtils._
import cats.syntax.all._
import cats.effect.{ExitCase, Sync}
import monix.execution.misc.NonFatal
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

private[tail] object IterantExitCallback {
  /**
    * Implementation for `Iterant#doOnEarlyStop`
    */
  def doOnEarlyStop[F[_], A](source: Iterant[F, A], f: F[Unit])
    (implicit F: Sync[F]): Iterant[F,A] = {

    source match {
      case Next(head, rest, stop) =>
        Next(head, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextCursor(items, rest, stop) =>
        NextCursor(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextBatch(items, rest, stop) =>
        NextBatch(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case ref @ (Halt(_) | Last(_)) =>
        ref // nothing to do
    }
  }

  /**
    * Implementation for `Iterant#doOnExitCase`
    */
  def doOnExitCase[F[_], A](fa: Iterant[F, A], f: ExitCase[Throwable] => F[Unit])(implicit F: Sync[F]): Iterant[F, A] = {
    def extractBatch(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    def signalAndSendError(stop: F[Unit], e: Throwable): Iterant[F, A] = {
      val next = stop.map { _ =>
        try {
          val stop = f(ExitCase.Error(e))
          Suspend[F, A](stop.map(_ => Halt(Some(e))), stop)
        } catch {
          case err if NonFatal(err) =>
            // In case `f` throws, we must still send the original error
            Halt[F, A](Some(e))
        }
      }
      Suspend[F, A](next, stop)
    }

    def tailGuard(ffa: F[Iterant[F, A]], stop: F[Unit]) =
      ffa.handleError(signalAndSendError(stop.flatMap(_ => f(canceled)), _))

    def loop(fa: Iterant[F, A]): Iterant[F, A] = {
      try fa match {
        case Next(a, lt, stop) =>
          val rest = tailGuard(lt, stop).map(loop)
          Next(a, rest, stop.flatMap(_ => f(canceled)))

        case NextCursor(cursor, rest, stop) =>
          try {
            val array = extractBatch(cursor)
            val next =
              if (cursor.hasNext())
                F.delay(loop(fa))
              else
                tailGuard(rest, stop).map(loop)

            array.length match {
              case 1 =>
                Next(array(0), next, stop.flatMap(_ => f(canceled)))
              case 0 =>
                Suspend(next, stop.flatMap(_ => f(canceled)))
              case _ =>
                NextCursor(BatchCursor.fromArray(array), next, stop.flatMap(_ => f(canceled)))
            }
          } catch {
            case e if NonFatal(e) =>
              signalAndSendError(stop, e)
          }

        case NextBatch(batch, rest, stop) =>
          try {
            loop(NextCursor(batch.cursor(), rest, stop.flatMap(_ => f(canceled))))
          } catch {
            case e if NonFatal(e) =>
              signalAndSendError(stop, e)
          }

        case Suspend(rest, stop) =>
          val fa = tailGuard(rest, stop).map(loop)
          Suspend(fa, stop.flatMap(_ => f(canceled)))

        case Last(_)  =>
          Suspend(f(ExitCase.Completed).map(_ => fa), f(canceled))

        case Halt(None) =>
          Suspend(f(ExitCase.Completed).map(_ => fa), f(canceled))

        case Halt(Some(e)) =>
          // In case `f` throws, we must still throw the original error
          try Suspend(f(ExitCase.Error(e)).attempt.map(_ => fa), f(canceled))
          catch { case err if NonFatal(err) => fa }

      } catch {
        case e if NonFatal(e) =>
          // Error happened due to calling the exit-case function, so we cannot
          // call it here again, because it is broken
          signalError(fa, e)
      }
    }

    Suspend(F.delay(loop(fa)), fa.earlyStop.flatMap(_ => f(canceled)))
  }

  private[this] val canceled: ExitCase[Throwable] = ExitCase.Canceled(None)
}
