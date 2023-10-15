/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import monix.execution.cancelables.CompositeCancelable
import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.execution.Ack.{ Continue, Stop }
import scala.util.control.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.{ Future, Promise }
import scala.util.Success

private[reactive] final class Zip2Observable[A1, A2, +R](obsA1: Observable[A1], obsA2: Observable[A2])(f: (A1, A2) => R)
  extends Observable[R] {

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    import out.scheduler

    val lock = new AnyRef
    // MUST BE synchronized by `lock`
    var isDone = false
    // MUST BE synchronized by `lock`
    var lastAck = Continue: Future[Ack]
    // MUST BE synchronized by `lock`
    var elemA1: A1 = null.asInstanceOf[A1]
    // MUST BE synchronized by `lock`
    var hasElemA1 = false
    // MUST BE synchronized by `lock`
    var elemA2: A2 = null.asInstanceOf[A2]
    // MUST BE synchronized by `lock`
    var hasElemA2 = false
    // MUST BE synchronized by `lock`
    var continueP = Promise[Ack]()
    // MUST BE synchronized by `lock`
    var completeWithNext = false

    // MUST BE synchronized by `lock`
    def rawOnNext(a1: A1, a2: A2): Future[Ack] =
      if (isDone) Stop
      else {
        var streamError = true
        try {
          val c = f(a1, a2)
          streamError = false
          val ack = out.onNext(c)
          if (completeWithNext) {
            ack.onComplete(_ => signalOnComplete(false))
          }
          ack
        } catch {
          case NonFatal(ex) if streamError =>
            isDone = true
            out.onError(ex)
            Stop
        } finally {
          hasElemA1 = false
          hasElemA2 = false
        }
      }

    // MUST BE synchronized by `lock`
    def signalOnNext(a1: A1, a2: A2): Future[Ack] = {
      lastAck = lastAck match {
        case Continue => rawOnNext(a1, a2)
        case Stop => Stop
        case async =>
          async.flatMap {
            // async execution, we have to re-sync
            case Continue => lock.synchronized(rawOnNext(a1, a2))
            case Stop => Stop
          }
      }

      val oldP = continueP
      continueP = Promise[Ack]()
      oldP.completeWith(lastAck)
      lastAck
    }

    def signalOnError(ex: Throwable): Unit = lock.synchronized {
      if (!isDone) {
        isDone = true
        out.onError(ex)
        lastAck = Stop
      }
    }

    def signalOnComplete(hasElem: Boolean): Unit = {
      def rawOnComplete(): Unit =
        if (!isDone) {
          isDone = true
          out.onComplete()
        }

      lock.synchronized {
        // Other source could already set completeWithNext
        // so it won't emit any elements
        if (!hasElem || completeWithNext) {
          lastAck match {
            case Continue => rawOnComplete()
            case Stop => () // do nothing
            case async =>
              async.onComplete {
                case Success(Continue) =>
                  lock.synchronized(rawOnComplete())
                case _ =>
                  () // do nothing
              }
          }

          continueP.trySuccess(Stop)
          lastAck = Stop
        } else {
          completeWithNext = true
        }
      }
    }

    val composite = CompositeCancelable()

    composite += obsA1.unsafeSubscribeFn(new Subscriber[A1] {
      implicit val scheduler: Scheduler = out.scheduler

      def onNext(elem: A1): Future[Ack] = lock.synchronized {
        if (isDone) Stop
        else {
          elemA1 = elem
          if (!hasElemA1) hasElemA1 = true

          if (hasElemA2)
            signalOnNext(elemA1, elemA2)
          else
            continueP.future
        }
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)
      def onComplete(): Unit =
        signalOnComplete(hasElemA1)
    })

    composite += obsA2.unsafeSubscribeFn(new Subscriber[A2] {
      implicit val scheduler: Scheduler = out.scheduler

      def onNext(elem: A2): Future[Ack] = lock.synchronized {
        if (isDone) Stop
        else {
          elemA2 = elem
          if (!hasElemA2) hasElemA2 = true

          if (hasElemA1)
            signalOnNext(elemA1, elemA2)
          else
            continueP.future
        }
      }

      def onError(ex: Throwable): Unit =
        signalOnError(ex)
      def onComplete(): Unit =
        signalOnComplete(hasElemA2)
    })

    composite
  }
}
