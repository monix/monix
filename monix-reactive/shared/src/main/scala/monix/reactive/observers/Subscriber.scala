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
trait Subscriber[-A] extends Observer[A] {
  implicit def scheduler: Scheduler
}

object Subscriber {
  /** Subscriber builder */
  def apply[A](observer: Observer[A], scheduler: Scheduler): Subscriber[A] =
    observer match {
      case ref: Subscriber[_] if ref.scheduler == scheduler =>
        ref.asInstanceOf[Subscriber[A]]
      case ref: Observer.Sync[_] =>
        Subscriber.Sync(ref.asInstanceOf[Observer.Sync[A]], scheduler)
      case _ =>
        new Implementation[A](observer, scheduler)
    }

  /** A `Subscriber.Sync` is a [[Subscriber]] whose `onNext` signal
    * is synchronous (i.e. the upstream observable doesn't need to
    * wait on a `Future` in order to decide whether to send the next event
    * or not).
    */
  trait Sync[-A] extends Subscriber[A] with Observer.Sync[A]

  object Sync {
    /** `Subscriber.Sync` builder */
    def apply[A](observer: Observer.Sync[A], scheduler: Scheduler): Subscriber.Sync[A] =
      observer match {
        case ref: Subscriber.Sync[_] if ref.scheduler == scheduler =>
          ref.asInstanceOf[Subscriber.Sync[A]]
        case _ =>
          new SyncImplementation[A](observer, scheduler)
      }
  }

  /** Helper for building an empty subscriber that doesn't do anything,
    * besides logging errors in case they happen.
    */
  def empty[A](implicit s: Scheduler): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = s
      def onNext(elem: A): Ack = Continue
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
      def onNext(elem: A): Ack = Stop
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
  def fromReactiveSubscriber[A](subscriber: RSubscriber[A], subscription: Cancelable)
    (implicit s: Scheduler): Subscriber[A] =
    ReactiveSubscriberAsMonixSubscriber(subscriber, subscription)

  /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    */
  def toReactiveSubscriber[A](subscriber: Subscriber[A]): RSubscriber[A] =
    toReactiveSubscriber(subscriber, subscriber.scheduler.executionModel.recommendedBatchSize)

  /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param requestCount a strictly positive number, representing the size
    *        of the buffer used and the number of elements requested on each
    *        cycle when communicating demand, compliant with the reactive
    *        streams specification
    */
  def toReactiveSubscriber[A](source: Subscriber[A], requestCount: Int): RSubscriber[A] =
    SubscriberAsReactiveSubscriber(source, requestCount)

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
  implicit class Extensions[A](val target: Subscriber[A]) extends AnyVal {
    /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      */
    def toReactive: RSubscriber[A] =
      Subscriber.toReactiveSubscriber(target)

    /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      *
      * @param requestCount a strictly positive number, representing the size
      *        of the buffer used and the number of elements requested on each
      *        cycle when communicating demand, compliant with the
      *        reactive streams specification
      */
    def toReactive(requestCount: Int): RSubscriber[A] =
      Subscriber.toReactiveSubscriber(target, requestCount)

    /** $feedCollectionDesc
      *
      * @param xs the traversable object containing the elements to feed
      *        into our subscriber
      */
    def onNextAll(xs: TraversableOnce[A]): Future[Ack] =
      Observer.feed(target, xs.toIterator)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param iterable is the collection of items to push downstream
      */
    def feed(iterable: Iterable[A]): Future[Ack] =
      Observer.feed(target, iterable)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param subscription $feedCancelableDesc
      * @param iterable is the collection of items to push downstream
      */
    def feed(subscription: BooleanCancelable, iterable: Iterable[A]): Future[Ack] =
      Observer.feed(target, subscription, iterable)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param iterator is the iterator of items to push downstream
      */
    def feed(iterator: Iterator[A]): Future[Ack] =
      Observer.feed(target, iterator)(target.scheduler)

    /** $feedCollectionDesc
      *
      * @param subscription $feedCancelableDesc
      * @param iterator is the iterator of items to push downstream
      */
    def feed(subscription: BooleanCancelable, iterator: Iterator[A]): Future[Ack] =
      Observer.feed(target, subscription, iterator)(target.scheduler)
  }

  private[this] final class Implementation[-A]
    (private val underlying: Observer[A], val scheduler: Scheduler)
    extends Subscriber[A] {

    require(underlying != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: A): Future[Ack] = underlying.onNext(elem)
    def onError(ex: Throwable): Unit = underlying.onError(ex)
    def onComplete(): Unit = underlying.onComplete()
  }

  private[this] final class SyncImplementation[-A]
    (observer: Observer.Sync[A], val scheduler: Scheduler)
    extends Subscriber.Sync[A] {

    require(observer != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: A): Ack = observer.onNext(elem)
    def onError(ex: Throwable): Unit = observer.onError(ex)
    def onComplete(): Unit = observer.onComplete()
  }
}
