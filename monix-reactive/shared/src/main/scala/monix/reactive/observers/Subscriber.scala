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

package monix.reactive.observers

import java.io.PrintStream
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observer
import monix.reactive.internal.rstreams._
import org.reactivestreams.{Subscriber => RSubscriber}
import scala.concurrent.Future

/** A `Subscriber` is an `Observer` with an attached `Scheduler`.
  *
  * A `Subscriber` can be seen as an address that the data source needs
  * in order to send events, along with an execution context.
  */
trait Subscriber[-T] extends Observer[T] {
  implicit def scheduler: Scheduler
}

object Subscriber {
  /** Subscriber builder */
  def apply[T](observer: Observer[T], scheduler: Scheduler): Subscriber[T] =
    observer match {
      case ref: Subscriber[_] if ref.scheduler == scheduler =>
        ref.asInstanceOf[Subscriber[T]]
      case ref: Observer.Sync[_] =>
        Subscriber.Sync(ref.asInstanceOf[Observer.Sync[T]], scheduler)
      case _ =>
        new Implementation[T](observer, scheduler)
    }

  /** A `Subscriber.Sync` is a [[Subscriber]] whose `onNext` signal
    * is synchronous (i.e. the upstream observable doesn't need to
    * wait on a `Future` in order to decide whether to send the next event
    * or not).
    */
  trait Sync[-T] extends Subscriber[T] with Observer.Sync[T]

  object Sync {
    /** `Subscriber.Sync` builder */
    def apply[T](observer: Observer.Sync[T], scheduler: Scheduler): Subscriber.Sync[T] =
      observer match {
        case ref: Subscriber.Sync[_] if ref.scheduler == scheduler =>
          ref.asInstanceOf[Subscriber.Sync[T]]
        case _ =>
          new SyncImplementation[T](observer, scheduler)
      }
  }

  /** Helper for building an empty subscriber that doesn't do anything,
    * besides logging errors in case they happen.
    */
  def empty[A](implicit s: Scheduler): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = s
      def onNext(elem: A): Continue = Continue
      def onError(ex: Throwable): Unit = s.reportFailure(ex)
      def onComplete(): Unit = ()
    }

  /** Helper for building an empty subscriber that doesn't do anything,
    * but that returns `Stop` on `onNext`.
    */
  def canceled[A](implicit s: Scheduler): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler: Scheduler = s
      def onError(ex: Throwable): Unit = s.reportFailure(ex)
      def onComplete(): Unit = ()
      def onNext(elem: A): Stop = Stop
    }

  /** Builds an [[Subscriber]] that just logs incoming events. */
  def dump[A](prefix: String, out: PrintStream = System.out)
    (implicit s: Scheduler): Subscriber.Sync[A] = {

    new Observer.DumpObserver[A](prefix, out) with Subscriber.Sync[A] {
      val scheduler = s
    }
  }

  /** Given an `org.reactivestreams.Subscriber` as defined by the
    * [[http://www.reactive-streams.org/ Reactive Streams]] specification,
    * it builds an [[Subscriber]] instance compliant with the
    * Monix Rx implementation.
    */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T], subscription: Cancelable)
    (implicit s: Scheduler): Subscriber[T] =
    ReactiveSubscriberAsMonixSubscriber(subscriber, subscription)

  /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    */
  def toReactiveSubscriber[T](subscriber: Subscriber[T]): RSubscriber[T] =
    toReactiveSubscriber(subscriber, subscriber.scheduler.executionModel.recommendedBatchSize)

  /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveSubscriber[T](source: Subscriber[T], bufferSize: Int): RSubscriber[T] = {
    source match {
      case sync: Subscriber.Sync[_] =>
        val inst = sync.asInstanceOf[Subscriber.Sync[T]]
        SyncSubscriberAsReactiveSubscriber(inst, bufferSize)
      case async =>
        SubscriberAsReactiveSubscriber(async, bufferSize)
    }
  }

  /** Extension methods for [[Subscriber]].
    *
    * @define feedCollectionDesc Feeds the source [[Subscriber]] with elements
    *         from the given collection, respecting the contract and returning
    *         a `Future[Ack]` with the last acknowledgement given after
    *         the last emitted element.
    *
    * @define feedCancelableDesc is a
    *         [[monix.execution.cancelables.BooleanCancelable BooleanCancelable]]
    *         that will be queried for its cancellation status, but only on
    *         asynchronous boundaries, and when it is seen as being `isCanceled`,
    *         streaming is stopped.
    */
  implicit class Extensions[T](val target: Subscriber[T]) extends AnyVal {
    /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      */
    def toReactive: RSubscriber[T] =
      Subscriber.toReactiveSubscriber(target)

    /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      *
      * @param bufferSize a strictly positive number, representing the size
      *                   of the buffer used and the number of elements requested
      *                   on each cycle when communicating demand, compliant with
      *                   the reactive streams specification
      */
    def toReactive(bufferSize: Int): RSubscriber[T] =
      Subscriber.toReactiveSubscriber(target, bufferSize)

    /** $feedCollectionDesc
      *
      * @param xs the traversable object containing the elements to feed
      *        into our subscriber
      */
    def onNextAll(xs: TraversableOnce[T]): Future[Ack] =
      Observer.feed(target, xs.toIterator)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param iterable is the collection of items to push downstream
      */
    def feed(iterable: Iterable[T]): Future[Ack] =
      Observer.feed(target, iterable)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param subscription $feedCancelableDesc
      * @param iterable is the collection of items to push downstream
      */
    def feed(subscription: BooleanCancelable, iterable: Iterable[T]): Future[Ack] =
      Observer.feed(target, subscription, iterable)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param iterator is the iterator of items to push downstream
      */
    def feed(iterator: Iterator[T]): Future[Ack] =
      Observer.feed(target, iterator)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param subscription $feedCancelableDesc
      * @param iterator is the iterator of items to push downstream
      */
    def feed(subscription: BooleanCancelable, iterator: Iterator[T]): Future[Ack] =
      Observer.feed(target, subscription, iterator)(target.scheduler)
  }

  private[this] final class Implementation[-T]
    (private val underlying: Observer[T], val scheduler: Scheduler)
    extends Subscriber[T] {

    require(underlying != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: T): Future[Ack] = underlying.onNext(elem)
    def onError(ex: Throwable): Unit = underlying.onError(ex)
    def onComplete(): Unit = underlying.onComplete()
  }

  private[this] final class SyncImplementation[-T]
    (observer: Observer.Sync[T], val scheduler: Scheduler)
    extends Subscriber.Sync[T] {

    require(observer != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: T): Ack = observer.onNext(elem)
    def onError(ex: Throwable): Unit = observer.onError(ex)
    def onComplete(): Unit = observer.onComplete()
  }
}
