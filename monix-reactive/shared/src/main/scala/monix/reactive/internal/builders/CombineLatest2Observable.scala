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

package monix.reactive.internal.builders

import monix.execution.{Ack, Cancelable}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.CompositeCancelable
import scala.util.control.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.Success

private[reactive] final
class CombineLatest2Observable[A1,A2,+R]
  (obsA1: Observable[A1], obsA2: Observable[A2])
  (f: (A1,A2) => R)
  extends Observable[R] { self =>

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    import out.scheduler

    var isDone = false
    // MUST BE synchronized by `self`
    var lastAck = Continue : Future[Ack]
    // MUST BE synchronized by `self`
    var elemA1: A1 = null.asInstanceOf[A1]
    // MUST BE synchronized by `self`
    var hasElemA1 = false
    // MUST BE synchronized by `self`
    var elemA2: A2 = null.asInstanceOf[A2]
    // MUST BE synchronized by `self`
    var hasElemA2 = false
    // MUST BE synchronized by `self`
    var completedCount = 0

    // MUST BE synchronized by `self`
    def rawOnNext(a1: A1, a2: A2): Future[Ack] =
      if (isDone) Stop else {
        var streamError = true
        try {
          val c = f(a1,a2)
          streamError = false
          out.onNext(c)
        } catch {
          case NonFatal(ex) if streamError =>
            isDone = true
            out.onError(ex)
            Stop
        }
      }

    // MUST BE synchronized by `self`
    def signalOnNext(a1: A1, a2: A2): Future[Ack] = {
      lastAck = lastAck match {
        case Continue => rawOnNext(a1,a2)
        case Stop => Stop
        case async =>
          async.flatMap {
            // async execution, we have to re-sync
            case Continue => self.synchronized(rawOnNext(a1,a2))
            case Stop => Stop
          }
      }

      lastAck
    }

    def signalOnError(ex: Throwable): Unit = self.synchronized {
      if (!isDone) {
        isDone = true
        out.onError(ex)
        lastAck = Stop
      }
    }

    def signalOnComplete(): Unit = self.synchronized  {
      completedCount += 1

      if (completedCount == 2 && !isDone) {
        lastAck match {
          case Continue =>
            isDone = true
            out.onComplete()
          case Stop =>
            () // do nothing
          case async =>
            async.onComplete {
              case Success(Continue) =>
                self.synchronized {
                  if (!isDone) {
                    isDone = true
                    out.onComplete()
                  }
                }
              case _ =>
                () // do nothing
            }
        }

        lastAck = Stop
      }
    }

    val composite = CompositeCancelable()

    composite += obsA1.unsafeSubscribeFn(new Subscriber[A1] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A1): Future[Ack] = self.synchronized {
        if (isDone) Stop else {
          elemA1 = elem
          if (!hasElemA1) hasElemA1 = true

          if (hasElemA2)
            signalOnNext(elemA1, elemA2)
          else
            Continue
        }
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)
      def onComplete(): Unit =
        signalOnComplete()
    })

    composite += obsA2.unsafeSubscribeFn(new Subscriber[A2] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A2): Future[Ack] = self.synchronized {
        if (isDone) Stop else {
          elemA2 = elem
          if (!hasElemA2) hasElemA2 = true

          if (hasElemA1)
            signalOnNext(elemA1, elemA2)
          else
            Continue
        }
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)
      def onComplete(): Unit =
        signalOnComplete()
    })

    composite
  }
}