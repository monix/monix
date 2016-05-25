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

package monix.reactive.subjects

import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{MulticastStrategy, OverflowStrategy}
import monix.reactive.OverflowStrategy.Synchronous
import monix.reactive.observers.{BufferedSubscriber, Subscriber, SyncObserver, SyncSubscriber}
import org.reactivestreams.{Processor => RProcessor, Subscriber => RSubscriber, Subscription}

/** A concurrent subject is meant for imperative style feeding of events.
  *
  * When emitting events, one doesn't need to follow the back-pressure contract.
  * On the other hand the grammar must still be respected:
  *
  *     (onNext)* (onComplete | onError)
  */
abstract class ConcurrentSubject[I,+O] extends Subject[I,O] with SyncObserver[I]

object ConcurrentSubject {
  def apply[A](multicast: MulticastStrategy[A], overflow: OverflowStrategy.Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    multicast match {
      case MulticastStrategy.Publish => ConcurrentSubject.publish[A](overflow)
      case MulticastStrategy.Behavior(initial) => ConcurrentSubject.behavior[A](initial, overflow)
      case MulticastStrategy.Async => ConcurrentSubject.async[A](overflow)
      case MulticastStrategy.Replay => ConcurrentSubject.replay[A](overflow)
      case MulticastStrategy.ReplayPopulated(initial) => ConcurrentSubject.replayPopulated[A](initial, overflow)
      case MulticastStrategy.ReplayLimited(capacity) => ConcurrentSubject.replayLimited[A](capacity, overflow)
    }

  /** Wraps any [[Subject]] into a [[ConcurrentSubject]].
    *
    * @param overflowStrategy - the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def from[I,O](p: Subject[I,O], overflowStrategy: Synchronous[I])
    (implicit s: Scheduler): ConcurrentSubject[I,O] =
    new SubjectAsConcurrent(p, overflowStrategy, s)

  /** Subject recipe for building [[PublishSubject publish]] subjects.
    *
    * @param strategy - the [[monix.reactive.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def publish[A](strategy: Synchronous[A])(implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(PublishSubject[A](), strategy)

  /** Subject recipe for building [[PublishToOneSubject]].
    *
    * @param strategy - the [[monix.reactive.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def publishToOne[A](strategy: Synchronous[A])(implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(PublishToOneSubject[A](), strategy)

  /** Subject recipe for building [[BehaviorSubject behavior]] subjects.
    *
    * @param initial the initial element to emit on subscribe,
    *        before the first `onNext` happens
    * @param strategy the [[monix.reactive.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def behavior[A](initial: A, strategy: Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(BehaviorSubject[A](initial), strategy)

  /** Subject recipe for building [[AsyncSubject async]] subjects.
    *
    * @param strategy the [[monix.reactive.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def async[A](strategy: Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(AsyncSubject[A](), strategy)

  /** Subject recipe for building [[ReplaySubject replay]] subjects.
    *
    * @param strategy the [[monix.reactive.OverflowStrategy overflow strategy]]
    *                 used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replay[A](strategy: Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(ReplaySubject[A](), strategy)

  /** Subject recipe for building [[ReplaySubject replay]] subjects.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    * @param strategy the [[monix.reactive.OverflowStrategy overflow strategy]]
    * used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replayPopulated[A](initial: Seq[A], strategy: Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(ReplaySubject[A](initial:_*), strategy)

  /** Subject recipe for building [[ReplaySubject replay]] subjects.
    * This variant creates a size-bounded replay subject.
    *
    * In this setting, the replay subject with a maximum capacity for
    * its internal buffer and discards the oldest item. The `capacity`
    * given is a guideline. The underlying implementation may decide
    * to optimize it (e.g. use the next power of 2 greater or equal to
    * the given value).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    * @param strategy the [[monix.reactive.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replayLimited[A](capacity: Int, strategy: Synchronous[A])
    (implicit s: Scheduler): ConcurrentSubject[A,A] =
    from(ReplaySubject.createWithSize[A](capacity), strategy)

  /** Transforms the source [[ConcurrentSubject]] into a `org.reactivestreams.Processor`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveProcessor[I,O](source: ConcurrentSubject[I,O], bufferSize: Int)
    (implicit s: Scheduler): RProcessor[I,O] = {

    new RProcessor[I,O] {
      private[this] val subscriber: RSubscriber[I] =
        Subscriber(source, s).toReactive(bufferSize)

      def subscribe(subscriber: RSubscriber[_ >: O]): Unit = {
        val sub = SingleAssignmentCancelable()
        sub := source.unsafeSubscribeFn(Subscriber.fromReactiveSubscriber(subscriber, sub))
      }

      def onSubscribe(s: Subscription): Unit = {
        subscriber.onSubscribe(s)
      }

      def onNext(t: I): Unit = {
        subscriber.onNext(t)
      }

      def onError(t: Throwable): Unit = {
        subscriber.onError(t)
      }

      def onComplete(): Unit = {
        subscriber.onComplete()
      }
    }
  }

  /** For converting normal subjects into concurrent ones */
  private final class SubjectAsConcurrent[I,+O] (
    subject: Subject[I, O],
    overflowStrategy: OverflowStrategy.Synchronous[I],
    scheduler: Scheduler)
    extends ConcurrentSubject[I,O] {

    private[this] val in: SyncSubscriber[I] =
      BufferedSubscriber.synchronous(Subscriber(subject, scheduler), overflowStrategy)

    def size: Int =
      subject.size
    def unsafeSubscribeFn(subscriber: Subscriber[O]): Cancelable =
      subject.unsafeSubscribeFn(subscriber)

    def onNext(elem: I): Ack = in.onNext(elem)
    def onError(ex: Throwable): Unit = in.onError(ex)
    def onComplete(): Unit = in.onComplete()
  }
}
