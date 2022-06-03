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
import scala.concurrent.{ Future, Promise }
import scala.util.Success

/** Given a sequence of priority/observable pairs, combines them into a new
  * observable that eagerly emits source items downstream as soon as demand is
  * signaled, choosing the item from the highest priority (greater numbers
  * mean higher priority) source when items from multiple sources are
  * available. If items are available from multiple sources with the same
  * highest priority, one of them is chosen arbitrarily.
  *
  * Source items are buffered only to the extent necessary to accommodate
  * backpressure from downstream, and thus if only a single item is available
  * when demand is signaled, it will be emitted regardless of priority.
  *
  * Backpressure is propagated from downstream to the source observables, so
  * that items from a given source will always be emitted downstream in the
  * same order as received from the source, and at most a single item from a
  * given source will be in flight at a time.
  */
private[reactive] final class MergePrioritizedListObservable[A](
  sources: Seq[(Int, Observable[A])]
) extends Observable[A] {

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    import out.scheduler

    val numberOfObservables = sources.size

    val lock = new AnyRef
    var isDone = false

    // MUST BE synchronized by `lock`
    var lastAck = Continue: Future[Ack]

    case class PQElem(data: A, promise: Promise[Ack], priority: Int)

    object PQElemOrdering extends Ordering[PQElem] {
      override def compare(x: PQElem, y: PQElem): Int =
        x.priority.compareTo(y.priority)
    }

    val pq = new mutable.PriorityQueue[PQElem]()(PQElemOrdering)

    // MUST BE synchronized by `lock`
    var completedCount = 0

    // MUST BE synchronized by `lock`
    def rawOnNext(a: A): Future[Ack] = {
      if (isDone) Stop
      else out.onNext(a).syncOnStopOrFailure(_ => lock.synchronized(completePromises()))
    }

    // MUST BE synchronized by `lock`
    def processNext(): Future[Ack] = {
      val e = pq.dequeue()
      val fut = rawOnNext(e.data)
      e.promise.completeWith(fut)
      fut
    }

    // MUST BE synchronized by `lock`
    def signalOnNext(): Future[Ack] = {
      lastAck = lastAck match {
        case Continue => processNext()
        case Stop => Stop
        case async =>
          async.flatMap {
            // async execution, we have to re-sync
            case Continue => lock.synchronized(processNext())
            case Stop => Stop
          }
      }
      lastAck
    }

    def signalOnError(ex: Throwable): Unit =
      lock.synchronized {
        if (!isDone) {
          isDone = true
          out.onError(ex)
          lastAck = Stop
          completePromises()
        }
      }

    def signalOnComplete(): Unit =
      lock.synchronized {
        completedCount += 1

        if (completedCount == numberOfObservables && !isDone) {
          lastAck match {
            case Continue =>
              isDone = true
              out.onComplete()
              completePromises()
            case Stop =>
              completePromises()
            case async =>
              async.onComplete {
                case Success(Continue) =>
                  lock.synchronized {
                    if (!isDone) {
                      isDone = true
                      out.onComplete()
                      completePromises()
                    }
                  }
                case _ =>
                  lock.synchronized(completePromises())
              }
          }

          lastAck = Stop
        }
      }

    // MUST BE synchronized by `lock`
    def completePromises(): Unit = {
      pq.iterator.foreach(e => e.promise.tryComplete(Success(Stop)))
    }

    val composite = CompositeCancelable()
    val priSources =
      sources.sorted(Ordering.by[(Int, Observable[A]), Int](_._1).reverse)

    priSources.foreach {
      case (pri, obs) =>
        composite += obs.unsafeSubscribeFn(new Subscriber[A] {
          implicit val scheduler: Scheduler = out.scheduler

          def onNext(elem: A): Future[Ack] =
            lock.synchronized {
              if (isDone) {
                Stop
              } else {
                val p = Promise[Ack]()
                pq.enqueue(PQElem(elem, p, pri))
                signalOnNext()
                p.future
              }
            }

          def onError(ex: Throwable): Unit =
            signalOnError(ex)

          def onComplete(): Unit = {
            signalOnComplete()
          }
        })
    }
    composite
  }
}
