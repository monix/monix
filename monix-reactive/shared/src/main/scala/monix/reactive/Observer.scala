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

package monix.reactive

import java.io.PrintStream

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.execution.{Ack, Cancelable, Scheduler, UncaughtExceptionReporter}
import monix.reactive.internal.rstreams._
import monix.reactive.observers.{Subscriber, SyncObserver, SyncSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Success
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
  /** Helper for building an empty observer that doesn't do anything,
    * besides logging errors in case they happen.
    */
  def empty[A](implicit r: UncaughtExceptionReporter): SyncObserver[A] =
    new SyncObserver[A] {
      def onNext(elem: A): Continue = Continue
      def onError(ex: Throwable): Unit = r.reportFailure(ex)
      def onComplete(): Unit = ()
    }

  /** Builds an [[Observer]] that just logs incoming events. */
  def dump[A](prefix: String, out: PrintStream = System.out): SyncObserver[A] =
    new DumpObserver[A](prefix, out)

  /** Given an `org.reactivestreams.Subscriber` as defined by the
    * [[http://www.reactive-streams.org/ Reactive Streams]] specification,
    * it builds an [[Observer]] instance compliant with the
    * Monix Rx implementation.
    */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T], subscription: Cancelable)
    (implicit s: Scheduler): Observer[T] =
    ReactiveSubscriberAsMonixSubscriber(subscriber, subscription)

  /** Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    */
  def toReactiveSubscriber[T](observer: Observer[T])(implicit s: Scheduler): RSubscriber[T] = {
    toReactiveSubscriber(observer, s.executionModel.recommendedBatchSize)(s)
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
  def feed[T](source: Observer[T], subscription: BooleanCancelable, iterable: Iterable[T])
    (implicit s: Scheduler): Future[Ack] = {

    try feed(source, subscription, iterable.iterator) catch {
      case NonFatal(ex) =>
        source.onError(ex)
        Stop
    }
  }

  /** Feeds the given [[Observer]] instance with elements from the given iterator,
    * respecting the contract and returning a `Future[Ack]` with the last
    * acknowledgement given after the last emitted element.
    */
  def feed[T](source: Observer[T], subscription: BooleanCancelable, iterator: Iterator[T])
    (implicit s: Scheduler): Future[Ack] = {

    def scheduleFeedLoop(promise: Promise[Ack], iterator: Iterator[T]): Future[Ack] = {
      s.execute(new Runnable {
        private[this] val em = s.executionModel

        @tailrec
        def fastLoop(syncIndex: Int): Unit = {
          val ack = source.onNext(iterator.next())

          if (iterator.hasNext) {
            val nextIndex =
              if (ack == Continue) em.nextFrameIndex(syncIndex)
              else if (ack == Stop) -1
              else 0

            if (nextIndex > 0)
              fastLoop(nextIndex)
            else if (nextIndex == 0 && !subscription.isCanceled)
              ack.onComplete {
                case Success(Continue) => run()
                case other => promise.complete(other)
              }
            else
              promise.success(Stop)
          } else {
            if ((ack eq Continue) || (ack eq Stop))
              promise.success(ack.asInstanceOf[Ack])
            else
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

      promise.future.syncTryFlatten
    }

    try {
      if (iterator.hasNext)
        scheduleFeedLoop(Promise[Ack](), iterator)
      else
        Continue
    } catch {
      case NonFatal(ex) =>
        source.onError(ex)
        Stop
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
    def feed(subscription: BooleanCancelable, iterable: Iterable[T])
      (implicit s: Scheduler): Future[Ack] =
      Observer.feed(source, subscription, iterable)

    /** Feeds the source [[Observer]] with elements from the given iterator,
      * respecting the contract and returning a `Future[Ack]` with the last
      * acknowledgement given after the last emitted element.
      */
    def feed(subscription: BooleanCancelable, iterator: Iterator[T])
      (implicit s: Scheduler): Future[Ack] =
      Observer.feed(source, subscription, iterator)
  }

  private[reactive] class DumpObserver[-A](prefix: String, out: PrintStream)
    extends SyncObserver[A] {

    private[this] var pos = 0

    def onNext(elem: A): Ack = {
      out.println(s"$pos: $prefix-->$elem")
      pos += 1
      Continue
    }

    def onError(ex: Throwable) = {
      out.println(s"$pos: $prefix-->$ex")
      pos += 1
    }

    def onComplete() = {
      out.println(s"$pos: $prefix completed")
      pos += 1
    }
  }
}
