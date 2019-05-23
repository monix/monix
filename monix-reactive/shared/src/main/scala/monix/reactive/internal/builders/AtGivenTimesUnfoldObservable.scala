/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.reactive.internal.builders

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.MultiAssignCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[reactive] class AtGivenTimesUnfoldObservable[A](start: A, next: A => Option[(A, Long)])
    extends Observable[A] {

  override def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    import subscriber.{scheduler => s}

    val o = subscriber
    val task = MultiAssignCancelable()

    def runnable(a: A, instant: Long) = new Runnable { self =>
      private[this] var prevInstant = instant
      private[this] var prevInput = a

      def scheduleNext(): Unit = {
        try {
          next(prevInput) match {
            case Some((nextInput, nextInstant)) =>
              // No need to synchronize, since we have a happens-before
              // relationship between scheduleOnce invocations.
              prevInstant = nextInstant
              prevInput = nextInput
              task := s.scheduleOnce(nextInstant - s.clockRealTime(MILLISECONDS), MILLISECONDS, self)
            case None =>
              o.onComplete()
          }
        } catch {
          case NonFatal(ex) =>
            try {
              o.onError(ex)
            } catch {
              case NonFatal(err) =>
                s.reportFailure(ex)
                s.reportFailure(err)
            }
        }
      }

      def asyncScheduleNext(r: Future[Ack]): Unit =
        r.onComplete {
          case Success(ack) =>
            if (ack == Continue) scheduleNext()
          case Failure(ex) =>
            s.reportFailure(ex)
        }

      def run(): Unit = {
        val ack = o.onNext(prevInput)

        if (ack == Continue)
          scheduleNext()
        else if (ack != Stop)
          asyncScheduleNext(ack)
      }
    }

    next(start) match {
      case Some((a, nextInstant)) =>
        // No need to synchronize, since we have a happens-before
        // relationship between scheduleOnce invocations.
        task := s.scheduleOnce(
          nextInstant - s.clockRealTime(MILLISECONDS),
          MILLISECONDS,
          runnable(a, nextInstant))
      case None =>
        o.onComplete()
        Cancelable.empty
    }
  }
}
