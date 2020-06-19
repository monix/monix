/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import java.util.concurrent.PriorityBlockingQueue

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Success

private[reactive] final class MergePrioritizedListObservable[A](sources: Seq[Observable[A]], priorities: Seq[Int])
  extends Observable[A] {
  require(sources.size == priorities.size, "sources.size != priorities.size")

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    import out.scheduler

    val numberOfObservables = sources.size

    val lock = new AnyRef
    var isDone = false

    // NOTE: We use arrays and other mutable structures here to be as performant as possible.

    // MUST BE synchronized by `lock`
    var lastAck = Continue: Future[Ack]

    case class PQElem(data: A, promise: Promise[Ack], priority: Int) extends Comparable[PQElem] {
      override def compareTo(o: PQElem): Int =
        priority.compareTo(o.priority)
    }

    val pq = new PriorityBlockingQueue[PQElem](sources.size)

    // MUST BE synchronized by `lock`
    var completedCount = 0

    // MUST BE synchronized by `lock`
    def rawOnNext(a: A): Future[Ack] = {
      if (isDone) Stop else out.onNext(a)
    }

    def processNext(): Future[Ack] = {
      val e = pq.remove()
      val fut = rawOnNext(e.data)
      e.promise.completeWith(fut)
      fut
    }

    // MUST BE synchronized by `lock`
    def signalOnNext(): Future[Ack] =
      lock.synchronized {
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
              () // do nothing
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
                  () // do nothing
              }
          }

          lastAck = Stop
        }
      }

    def completePromises(): Unit = {
      pq.iterator().asScala.foreach(e => e.promise.complete(Success(Stop)))
    }

    val composite = CompositeCancelable()

    sources.zip(priorities).foreach { pair =>
      composite += pair._1.unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler: Scheduler = out.scheduler

        def onNext(elem: A): Future[Ack] = {
          if (isDone) {
            Stop
          } else {
            pq.add(PQElem(elem, Promise(), pair._2))
            signalOnNext()
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
