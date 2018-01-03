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

import monix.execution.Ack.{Stop, Continue}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.{Future, Promise}

private[reactive] final class Interleave2Observable[+A]
  (obsA1: Observable[A], obsA2: Observable[A]) extends Observable[A] { self =>

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    import out.scheduler

    // MUST BE synchronized by `self`
    var isDone = false
    // MUST BE synchronized by `self`
    var downstreamAck = Continue : Future[Ack]
    // MUST BE synchronized by `self`.
    // This essentially serves as a lock for obsA1 when `select` is not assigned to it
    // pauseA1 is initialized to be `Continue`, so that obsA1 is deterministically emitted before obsA2
    var pauseA1 = Promise.successful(Continue : Ack)
    // This essentially serves as a lock for obsA2 when `select` is not assigned to it
    var pauseA2 = Promise[Ack]()

    // MUST BE synchronized by `self`
    var completedCount = 0
    var lastAck1 = Continue : Future[Ack]
    var lastAck2 = Continue : Future[Ack]

    def signalOnError(ex: Throwable): Unit =
      self.synchronized {
        if (!isDone) {
          isDone = true
          out.onError(ex)
          downstreamAck = Stop
          pauseA1.tryCompleteWith(Stop)
          pauseA2.tryCompleteWith(Stop)
        }
      }

    // MUST BE synchronized by `self`
    def signalOnComplete(ack: Future[Ack]): Unit = {
      val shouldComplete = !isDone && {
        completedCount += 1
        completedCount >= 2
      }

      if (shouldComplete)
        ack.syncOnContinue(
          self.synchronized(if (!isDone) {
            isDone = true
            out.onComplete()
          }))
    }

    val composite = CompositeCancelable()

    composite += obsA1.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = self.synchronized {
        @inline def sendSignal(a: A): Future[Ack] = self.synchronized {
          if (isDone) Stop else {
            downstreamAck = out.onNext(a)
            pauseA1 = Promise[Ack]()
            pauseA2.tryCompleteWith(downstreamAck)
            downstreamAck
          }
        }

        // Pausing A1 until obsA2 allows us to send
        lastAck1 = pauseA1.future.syncTryFlatten.syncFlatMap {
          case Continue => sendSignal(elem)
          case Stop => Stop
        }

        lastAck1
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)

      def onComplete(): Unit = self.synchronized {
        lastAck1.syncOnContinue {
          signalOnComplete(lastAck1)
          pauseA2.trySuccess(Continue)
          pauseA2 = Promise.successful(Continue)
        }
      }
    })

    composite += obsA2.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = self.synchronized {
        @inline def sendSignal(a: A): Future[Ack] = self.synchronized {
          if (isDone) Stop else {
            downstreamAck = out.onNext(a)
            pauseA2 = Promise[Ack]()
            pauseA1.tryCompleteWith(downstreamAck)
            downstreamAck
          }
        }

        // Pausing A2 until obsA1 allows us to send
        lastAck2 = pauseA2.future.syncTryFlatten.syncFlatMap {
          case Continue => sendSignal(elem)
          case Stop => Stop
        }

        lastAck2
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)

      def onComplete(): Unit = self.synchronized {
        lastAck2.syncOnContinue {
          signalOnComplete(lastAck2)
          pauseA1.trySuccess(Continue)
          pauseA1 = Promise.successful(Continue)
        }
      }
    })

    composite
  }
}
