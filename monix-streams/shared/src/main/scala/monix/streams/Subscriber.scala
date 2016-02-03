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
import monix.streams.internal.reactivestreams.{ReactiveSubscriberAsMonixSubscriber, SubscriberAsReactiveSubscriber, SyncSubscriberAsReactiveSubscriber}
import monix.streams.observers.{SyncObserver, SyncSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}
import scala.concurrent.Future

/** A `Subscriber` value is a named tuple of an observer and a scheduler,
  * whose usage is in [[Observable.unsafeCreate]].
  *
  * An `Observable.subscribe` takes as parameters both an [[Observer]]
  * and a [[monix.execution.Scheduler Scheduler]] and the purpose of a
  * `Subscriber` is convenient grouping in `Observable.create`.
  *
  * A `Subscriber` value is basically an address that the data source needs
  * in order to send events.
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
      case ref: SyncObserver[_] =>
        SyncSubscriber(ref.asInstanceOf[SyncObserver[T]], scheduler)
      case _ =>
        new Implementation[T](observer, scheduler)
    }

  /** Given an `org.reactivestreams.Subscriber` as defined by the
    * [[http://www.reactive-streams.org/ Reactive Streams]] specification,
    * it builds an [[Subscriber]] instance compliant with the
    * Monix Rx implementation.
    */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T])(implicit s: Scheduler): Subscriber[T] = {
    ReactiveSubscriberAsMonixSubscriber(subscriber)
  }

  /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    */
  def toReactiveSubscriber[T](subscriber: Subscriber[T]): RSubscriber[T] = {
    toReactiveSubscriber(subscriber, Platform.recommendedBatchSize)
  }

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
      case sync: SyncSubscriber[_] =>
        val inst = sync.asInstanceOf[SyncSubscriber[T]]
        SyncSubscriberAsReactiveSubscriber(inst, bufferSize)
      case async =>
        SubscriberAsReactiveSubscriber(async, bufferSize)
    }
  }

  /** Extension methods for [[Subscriber]]. */
  implicit class Extensions[T](val source: Subscriber[T]) extends AnyVal {
    /** Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
      * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
      * specification.
      */
    def toReactive: RSubscriber[T] =
      Subscriber.toReactiveSubscriber(source)

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
      Subscriber.toReactiveSubscriber(source, bufferSize)

    /** Feeds the source [[Subscriber]] with elements from the given iterable,
      * respecting the contract and returning a `Future[Ack]` with the last
      * acknowledgement given after the last emitted element.
      */
    def feed(iterable: Iterable[T]): Future[Ack] =
      Observer.feed(source, iterable)(source.scheduler)

    /** Feeds the source [[Subscriber]] with elements from the given iterator,
      * respecting the contract and returning a `Future[Ack]` with the last
      * acknowledgement given after the last emitted element.
      */
    def feed(iterator: Iterator[T]): Future[Ack] =
      Observer.feed(source, iterator)(source.scheduler)
  }

  private[this] final class Implementation[-T]
    (private val underlying: Observer[T], val scheduler: Scheduler)
    extends Subscriber[T] {

    require(underlying != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: T): Future[Ack] = underlying.onNext(elem)
    def onError(ex: Throwable): Unit = underlying.onError(ex)
    def onComplete(): Unit = underlying.onComplete()

    override def equals(other: Any): Boolean = other match {
      case that: Implementation[_] =>
        underlying == that.underlying && scheduler == that.scheduler
      case _ =>
        false
    }

    override def hashCode(): Int = {
      31 * underlying.hashCode() + scheduler.hashCode()
    }
  }
}
