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

package monix.reactive.internal.builders

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{ Ack, Cancelable }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.{ Future, Promise }

private[reactive] final class Interleave2Observable[+A](obsA1: Observable[A], obsA2: Observable[A])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    import out.scheduler

    val lock = new AnyRef
    // MUST BE synchronized by `lock`
    var isDone = false
    // MUST BE synchronized by `lock`
    var downstreamAck = Continue: Future[Ack]
    // MUST BE synchronized by `lock`.
    // This essentially serves as a lock for obsA1 when `select` is not assigned to it
    // pauseA1 is initialized to be `Continue`, so that obsA1 is deterministically emitted before obsA2
    var pauseA1 = Promise.successful(Continue: Ack)
    // This essentially serves as a lock for obsA2 when `select` is not assigned to it
    var pauseA2 = Promise[Ack]()

    // MUST BE synchronized by `lock`
    var completedCount = 0
    var lastAck1 = Continue: Future[Ack]
    var lastAck2 = Continue: Future[Ack]

    def signalOnError(ex: Throwable): Unit =
      lock.synchronized {
        if (!isDone) {
          isDone = true
          out.onError(ex)
          downstreamAck = Stop
          pauseA1.completeWith(Stop)
          pauseA2.completeWith(Stop)
          ()
        }
      }

    // MUST BE synchronized by `lock`
    def signalOnComplete(ack: Future[Ack]): Unit = {
      val shouldComplete = !isDone && {
        completedCount += 1
        completedCount >= 2
      }

      if (shouldComplete) {
        ack.syncOnContinue(lock.synchronized(if (!isDone) {
          isDone = true
          out.onComplete()
        }))
        ()
      }
    }

    val composite = CompositeCancelable()

    composite += obsA1.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = lock.synchronized {
        def sendSignal(elem: A): Future[Ack] = lock.synchronized {
          if (isDone) Stop
          else {
            downstreamAck = out.onNext(elem)
            pauseA1 = Promise[Ack]()
            pauseA2.completeWith(downstreamAck)
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

      def onComplete(): Unit = lock.synchronized {
        lastAck1.syncOnContinue {
          signalOnComplete(lastAck1)
          pauseA2.trySuccess(Continue)
          pauseA2 = Promise.successful(Continue)
        }
        ()
      }
    })

    composite += obsA2.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = lock.synchronized {
        def sendSignal(elem: A): Future[Ack] = lock.synchronized {
          if (isDone) Stop
          else {
            downstreamAck = out.onNext(elem)
            pauseA2 = Promise[Ack]()
            pauseA1.completeWith(downstreamAck)
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

      def onComplete(): Unit = lock.synchronized {
        lastAck2.syncOnContinue {
          signalOnComplete(lastAck2)
          pauseA1.trySuccess(Continue)
          pauseA1 = Promise.successful(Continue)
        }
        ()
      }
    })

    composite
  }
}
