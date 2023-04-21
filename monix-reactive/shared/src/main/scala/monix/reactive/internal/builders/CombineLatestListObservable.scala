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

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success

/** Only used in [[Observable.combineLatestList()]]. */
private[reactive] final class CombineLatestListObservable[A](observables: Seq[Observable[A]])
  extends Observable[Seq[A]] {

  def unsafeSubscribeFn(out: Subscriber[Seq[A]]): Cancelable = {
    import out.scheduler

    val numberOfObservables = observables.size

    val lock = new AnyRef
    var isDone = false

    // NOTE: We use arrays and other mutable structures here to be as performant as possible.

    // MUST BE synchronized by `lock`
    var lastAck = Continue: Future[Ack]
    // MUST BE synchronized by `lock`
    val elems: mutable.IndexedSeq[A] = mutable.IndexedSeq.fill(numberOfObservables)(null.asInstanceOf[A])
    // MUST BE synchronized by `lock`
    val hasElems: Array[Boolean] = Array.fill(numberOfObservables)(false)

    var hasElemsCount: Int = 0

    // MUST BE synchronized by `lock`
    var completedCount = 0

    // MUST BE synchronized by `lock`
    def rawOnNext(as: Seq[A]): Future[Ack] = {
      if (isDone) Stop else out.onNext(as)
    }

    // MUST BE synchronized by `lock`
    def signalOnNext(as: Seq[A]): Future[Ack] = {
      lastAck = lastAck match {
        case Continue => rawOnNext(as)
        case Stop => Stop
        case async =>
          async.flatMap {
            // async execution, we have to re-sync
            case Continue => lock.synchronized(rawOnNext(as))
            case Stop => Stop
          }
      }

      lastAck
    }

    def signalOnError(ex: Throwable): Unit = lock.synchronized {
      if (!isDone) {
        isDone = true
        out.onError(ex)
        lastAck = Stop
      }
    }

    def signalOnComplete(): Unit = lock.synchronized {
      completedCount += 1

      if (completedCount == numberOfObservables && !isDone) {
        lastAck match {
          case Continue =>
            isDone = true
            out.onComplete()
          case Stop =>
            () // do nothing
          case async =>
            async.onComplete {
              case Success(Continue) =>
                lock.synchronized {
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

    var i: Int = 0
    observables.foreach { obs =>
      val index: Int = i
      i += 1

      composite += obs.unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler: Scheduler = out.scheduler

        def onNext(elem: A): Future[Ack] = lock.synchronized {
          if (isDone) {
            Stop
          } else {
            elems(index) = elem
            if (!hasElems(index)) {
              hasElems(index) = true
              hasElemsCount += 1
            }

            if (hasElemsCount == numberOfObservables) {
              signalOnNext(elems.toVector)
            } else {
              Continue
            }
          }
        }

        def onError(ex: Throwable): Unit =
          signalOnError(ex)
        def onComplete(): Unit =
          signalOnComplete()
      })
    }
    composite
  }
}
