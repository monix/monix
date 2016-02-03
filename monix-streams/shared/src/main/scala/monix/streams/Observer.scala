/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams

import monix.execution.Scheduler
import monix.execution.internal.Platform
import monix.streams.Ack.{Continue, Cancel}
import monix.streams.internal.reactivestreams._
import monix.streams.observers.{SyncObserver, SyncSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal


/** The Observer from the Rx pattern is the trio of callbacks that
  * get subscribed to an Observable for receiving events.
  *
  * The events received must follow the Rx grammar, which is:
  *      onNext *   (onComplete | onError)?
  *
  * That means an Observer can receive zero or multiple events, the stream
  * ending either in one or zero `onComplete` or `onError` (just one, not both),
  * and after onComplete or onError, a well behaved Observable implementation
  * shouldn't send any more onNext events.
  */
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]

  def onError(ex: Throwable): Unit

  def onComplete(): Unit
}

object Observer {
  /** Given an `org.reactivestreams.Subscriber` as defined by the
    * [[http://www.reactive-streams.org/ Reactive Streams]] specification,
    * it builds an [[Observer]] instance compliant with the
    * Monix Rx implementation.
    */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T])(implicit s: Scheduler): Subscriber[T] = {
    ReactiveSubscriberAsMonixSubscriber(subscriber)
  }

  /** Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    */
  def toReactiveSubscriber[T](observer: Observer[T])(implicit s: Scheduler): RSubscriber[T] = {
    toReactiveSubscriber(observer, Platform.recommendedBatchSize)(s)
  }

  /** Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveSubscriber[T](observer: Observer[T], bufferSize: Int)(implicit s: Scheduler): RSubscriber[T] = {
    require(bufferSize > 0, "requestCount > 0")
    observer match {
      case sync: SyncObserver[_] =>
        val inst = sync.asInstanceOf[SyncObserver[T]]
        SyncSubscriberAsReactiveSubscriber(SyncSubscriber(inst, s), bufferSize)
      case async =>
        SubscriberAsReactiveSubscriber(Subscriber(async, s), bufferSize)
    }
  }

  /** Feeds the given [[Observer]] instance with elements from the given iterable,
    * respecting the contract and returning a `Future[Ack]` with the last
    * acknowledgement given after the last emitted element.
    */
  def feed[T](source: Observer[T], iterable: Iterable[T])(implicit s: Scheduler): Future[Ack] = {
    try feed(source, iterable.iterator) catch {
      case NonFatal(ex) =>
        source.onError(ex)
        Cancel
    }
  }

  /** Feeds the given [[Observer]] instance with elements from the given iterator,
    * respecting the contract and returning a `Future[Ack]` with the last
    * acknowledgement given after the last emitted element.
    */
  def feed[T](source: Observer[T], iterator: Iterator[T])(implicit s: Scheduler): Future[Ack] = {
    def scheduleFeedLoop(promise: Promise[Ack], iterator: Iterator[T]): Future[Ack] = {
      s.execute(new Runnable {
        private[this] val modulus = Platform.recommendedBatchSize - 1

        @tailrec
        def fastLoop(syncIndex: Int): Unit = {
          val ack = source.onNext(iterator.next())

          if (iterator.hasNext) {
            val nextIndex = if (!ack.isCompleted) 0 else
              (syncIndex + 1) & modulus

            if (nextIndex != 0) {
              if (ack == Continue || ack.value.get == Continue.AsSuccess)
                fastLoop(nextIndex)
              else
                promise.complete(ack.value.get)
            }
            else ack.onComplete {
              case Continue.AsSuccess =>
                run()
              case other =>
                promise.complete(other)
            }
          }
          else {
            promise.completeWith(ack)
          }
        }

        def run(): Unit = {
          try fastLoop(0) catch {
            case NonFatal(ex) =>
              try source.onError(ex) finally {
                promise.failure(ex)
              }
          }
        }
      })

      promise.future
    }

    try {
      if (iterator.hasNext)
        scheduleFeedLoop(Promise[Ack](), iterator)
      else
        Continue
    }
    catch {
      case NonFatal(ex) =>
        source.onError(ex)
        Cancel
    }
  }

  /** Extension methods for [[Observer]]. */
  implicit class Extensions[T](val source: Observer[T]) extends AnyVal {
    /** Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      */
    def toReactive(implicit s: Scheduler): RSubscriber[T] =
      Observer.toReactiveSubscriber(source)

    /** Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      *
      * @param bufferSize a strictly positive number, representing the size
      *                   of the buffer used and the number of elements requested
      *                   on each cycle when communicating demand, compliant with
      *                   the reactive streams specification
      */
    def toReactive(bufferSize: Int)(implicit s: Scheduler): RSubscriber[T] =
      Observer.toReactiveSubscriber(source, bufferSize)

    /** Feeds the source [[Observer]] with elements from the given iterable,
      * respecting the contract and returning a `Future[Ack]` with the last
      * acknowledgement given after the last emitted element.
      */
    def feed(iterable: Iterable[T])(implicit s: Scheduler): Future[Ack] =
      Observer.feed(source, iterable)

    /** Feeds the source [[Observer]] with elements from the given iterator,
      * respecting the contract and returning a `Future[Ack]` with the last
      * acknowledgement given after the last emitted element.
      */
    def feed(iterator: Iterator[T])(implicit s: Scheduler): Future[Ack] =
      Observer.feed(source, iterator)
  }
}
