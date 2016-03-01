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

import java.util.concurrent.CancellationException

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.{Ack, Cancelable, CancelableFuture, Scheduler}
import monix.streams.ObservableLike.{Operator, Transformer}
import monix.streams.internal.concurrent.UnsafeSubscribeRunnable
import monix.streams.internal.builders
import monix.streams.observables._
import monix.streams.observers._
import monix.streams.subjects._
import org.reactivestreams.{Publisher => RPublisher, Subscriber => RSubscriber}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Future, Promise}
import scala.language.{higherKinds, implicitConversions}
import scala.util.control.NonFatal

/** The Observable type that implements the Reactive Pattern.
  *
  * Provides methods of subscribing to the Observable and operators
  * for combining observable sources, filtering, modifying,
  * throttling, buffering, error handling and others.
  *
  * The [[Observable$ companion object]] contains builders for
  * creating Observable instances.
  *
  * See the available documentation at: [[https://monix.io]]
  *
  * @define concatDescription Concatenates the sequence
  *         of Observables emitted by the source into one Observable,
  *         without any transformation.
  *
  *         You can combine the items emitted by multiple Observables
  *         so that they act like a single Observable by using this
  *         operator.
  *
  *         The difference between the `concat` operation and
  *         [[Observable!.merge[B](implicit* merge]] is that `concat`
  *         cares about ordering of emitted items (e.g. all items
  *         emitted by the first observable in the sequence will come
  *         before the elements emitted by the second observable),
  *         whereas `merge` doesn't care about that (elements get
  *         emitted as they come). Because of back-pressure applied to
  *         observables, [[Observable!.concat]] is safe to use in all
  *         contexts, whereas `merge` requires buffering.
  * @define concatReturn an Observable that emits items that are the result of
  *         flattening the items emitted by the Observables emitted by `this`
  * @define mergeMapDescription Creates a new Observable by applying a
  *         function that you supply to each item emitted by the
  *         source Observable, where that function returns an
  *         Observable, and then merging those resulting Observables
  *         and emitting the results of this merger.
  *
  *         This function is the equivalent of `observable.map(f).merge`.
  *
  *         The difference between [[Observable!.concat concat]] and
  *         `merge` is that `concat` cares about ordering of emitted
  *         items (e.g. all items emitted by the first observable in
  *         the sequence will come before the elements emitted by the
  *         second observable), whereas `merge` doesn't care about
  *         that (elements get emitted as they come). Because of
  *         back-pressure applied to observables, [[Observable!.concat concat]]
  *         is safe to use in all contexts, whereas
  *         [[Observable!.merge[B](implicit* merge]] requires
  *         buffering.
  * @define mergeMapReturn an Observable that emits the result of applying the
  *         transformation function to each item emitted by the source
  *         Observable and merging the results of the Observables
  *         obtained from this transformation.
  * @define mergeDescription Merges the sequence of Observables emitted by
  *         the source into one Observable, without any transformation.
  *
  *         You can combine the items emitted by multiple Observables
  *         so that they act like a single Observable by using this
  *         operator.
  * @define mergeReturn an Observable that emits items that are the
  *         result of flattening the items emitted by the Observables
  *         emitted by `this`.
  * @define overflowStrategyParam the [[OverflowStrategy overflow strategy]]
  *         used for buffering, which specifies what to do in case
  *         we're dealing with a slow consumer - should an unbounded
  *         buffer be used, should back-pressure be applied, should
  *         the pipeline drop newer or older events, should it drop
  *         the whole buffer? See [[OverflowStrategy]] for more
  *         details.
  * @define onOverflowParam a function that is used for signaling a special
  *         event used to inform the consumers that an overflow event
  *         happened, function that receives the number of dropped
  *         events as a parameter (see [[OverflowStrategy.Evicted]])
  * @define delayErrorsDescription This version
  *         is reserving onError notifications until all of the
  *         Observables complete and only then passing the issued
  *         errors(s) along to the observers. Note that the streamed
  *         error is a
  *         [[monix.streams.exceptions.CompositeException CompositeException]],
  *         since multiple errors from multiple streams can happen.
  * @define defaultOverflowStrategy this operation needs to do buffering
  *         and by not specifying an [[OverflowStrategy]], the
  *         [[OverflowStrategy.Default default strategy]] is being
  *         used.
  * @define asyncBoundaryDescription Forces a buffered asynchronous boundary.
  *
  *         Internally it wraps the observer implementation given to
  *         `onSubscribe` into a
  *         [[monix.streams.observers.BufferedSubscriber BufferedSubscriber]].
  *
  *         Normally Monix's implementation guarantees that events are
  *         not emitted concurrently, and that the publisher MUST NOT
  *         emit the next event without acknowledgement from the
  *         consumer that it may proceed, however for badly behaved
  *         publishers, this wrapper provides the guarantee that the
  *         downstream [[monix.streams.Observer Observer]] given in
  *         `subscribe` will not receive concurrent events.
  *
  *         WARNING: if the buffer created by this operator is
  *         unbounded, it can blow up the process if the data source
  *         is pushing events faster than what the observer can
  *         consume, as it introduces an asynchronous boundary that
  *         eliminates the back-pressure requirements of the data
  *         source. Unbounded is the default
  *         [[monix.streams.OverflowStrategy overflowStrategy]], see
  *         [[monix.streams.OverflowStrategy OverflowStrategy]] for
  *         options.
  */
abstract class Observable[+A] extends ObservableLike[A, Observable] { self =>
  /** Characteristic function for an `Observable` instance, that creates
    * the subscription and that eventually starts the streaming of
    * events to the given [[Observer]], being meant to be provided.
    *
    * This function is "unsafe" to call because it does not protect
    * the calls to the given [[Observer]] implementation in regards to
    * unexpected exceptions that violate the contract, therefore the
    * given instance must respect its contract and not throw any
    * exceptions when the observable calls `onNext`, `onComplete` and
    * `onError`. If it does, then the behavior is undefined.
    *
    * @see [[Observable.subscribe(observer* subscribe]].
    */
  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable

  def unsafeSubscribeFn(observer: Observer[A])(implicit s: Scheduler): Cancelable =
    unsafeSubscribeFn(Subscriber(observer,s))

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe(subscriber: Subscriber[A]): Cancelable = {
    unsafeSubscribeFn(SafeSubscriber[A](subscriber))
  }

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe(observer: Observer[A])(implicit s: Scheduler): Cancelable =
    subscribe(Subscriber(observer, s))

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe(nextFn: A => Future[Ack], errorFn: Throwable => Unit, completedFn: () => Unit)
    (implicit s: Scheduler): Cancelable = {

    subscribe(new Subscriber[A] {
      implicit val scheduler = s
      def onNext(elem: A) = nextFn(elem)
      def onComplete() = completedFn()
      def onError(ex: Throwable) = errorFn(ex)
    })
  }

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe(nextFn: A => Future[Ack], errorFn: Throwable => Unit)(implicit s: Scheduler): Cancelable =
    subscribe(nextFn, errorFn, () => ())

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe()(implicit s: Scheduler): Cancelable =
    subscribe(elem => Continue)

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    */
  def subscribe(nextFn: A => Future[Ack])(implicit s: Scheduler): Cancelable =
    subscribe(nextFn, error => s.reportFailure(error), () => ())

  /** Transforms the source using the given operator. */
  override def lift[B](operator: Operator[A, B]): Observable[B] =
    new Observable[B] {
      def unsafeSubscribeFn(subscriber: Subscriber[B]): Cancelable = {
        val sb = operator(subscriber)
        self.unsafeSubscribeFn(sb)
      }
    }

  /** Transforms the source using the given transformer function. */
  override def transform[B](transformer: Transformer[A, B]): Observable[B] =
    transformer(this)

  /** Wraps this Observable into a `org.reactivestreams.Publisher`.
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    */
  def toReactive[B >: A](implicit s: Scheduler): RPublisher[B] =
    new RPublisher[B] {
      def subscribe(subscriber: RSubscriber[_ >: B]): Unit = {
        val subscription = SingleAssignmentCancelable()
        subscription := unsafeSubscribeFn(SafeSubscriber(
          Subscriber.fromReactiveSubscriber(subscriber, subscription)
        ))
      }
    }

  /** Given the source observable and another `Observable`, emits all of
    * the items from the first of these Observables to emit an item
    * and cancel the other.
    */
  def ambWith[B >: A](other: Observable[B]): Observable[B] = {
    Observable.amb(self, other)
  }

  /** Returns an Observable which only emits the first item for which
    * the predicate holds.
    *
    * @param p is a function that evaluates the items emitted by the
    *        source Observable, returning `true` if they pass the filter
    *
    * @return an Observable that emits only the first item in the original
    *         Observable for which the filter evaluates as `true`
    */
  def find(p: A => Boolean): Observable[A] =
    filter(p).head

  /** Returns an Observable which emits a single value, either true, in
    * case the given predicate holds for at least one item, or false
    * otherwise.
    *
    * @param p is a function that evaluates the items emitted by the
    *        source Observable, returning `true` if they pass the
    *        filter
    *
    * @return an Observable that emits only true or false in case
    *         the given predicate holds or not for at least one item
    */
  def exists(p: A => Boolean): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  /** Returns an Observable that emits a single boolean, either true, in
    * case the given predicate holds for all the items emitted by the
    * source, or false in case at least one item is not verifying the
    * given predicate.
    *
    * @param p is a function that evaluates the items emitted by the source
    *        Observable, returning `true` if they pass the filter
    *
    * @return an Observable that emits only true or false in case the given
    *         predicate holds or not for all the items
    */
  def forAll(p: A => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  /** Creates a new Observable that emits the given element and then it
    * also emits the events of the source (prepend operation).
    */
  def +:[B >: A](elem: B): Observable[B] =
    Observable.now(elem) ++ this

  /** Creates a new Observable that emits the given elements and then it
    * also emits the events of the source (prepend operation).
    */
  def startWith[B >: A](elems: B*): Observable[B] =
    Observable.from(elems) ++ this

  /** Creates a new Observable that emits the events of the source and
    * then it also emits the given element (appended to the stream).
    */
  def :+[B >: A](elem: B): Observable[B] =
    this ++ Observable.now(elem)

  /** Creates a new Observable that emits the events of the source and
    * then it also emits the given elements (appended to the stream).
    */
  def endWith[B >: A](elems: B*): Observable[B] =
    this ++ Observable.from(elems)

  /** Only emits the first element emitted by the source observable,
    * after which it's completed immediately.
    */
  def head: Observable[A] = take(1)

  /** Drops the first element of the source observable,
    * emitting the rest.
    */
  def tail: Observable[A] = drop(1)

  /** Only emits the last element emitted by the source observable,
    * after which it's completed immediately.
    */
  def last: Observable[A] =
    takeRight(1)

  /** Emits the first element emitted by the source, or otherwise if the
    * source is completed without emitting anything, then the
    * `default` is emitted.
    */
  def headOrElse[B >: A](default: => B): Observable[B] =
    head.foldLeft(Option.empty[B])((_, elem) => Some(elem)) map {
      case Some(elem) => elem
      case None => default
    }

  /** Emits the first element emitted by the source, or otherwise if the
    * source is completed without emitting anything, then the
    * `default` is emitted.
    *
    * Alias for `headOrElse`.
    */
  def firstOrElse[B >: A](default: => B): Observable[B] =
    headOrElse(default)

  /** Returns a new Observable that uses the specified `Scheduler` for
    * initiating the subscription.
    */
  def subscribeOn(s: Scheduler): Observable[A] = {
    Observable.unsafeCreate(o => s.execute(UnsafeSubscribeRunnable(this, o)))
  }

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers).
    *
    * This operator is unsafe because `Subject` objects are stateful
    * and have to obey the `Observer` contract, meaning that they
    * shouldn't be subscribed multiple times, so they are error
    * prone. Only use if you know what you're doing, otherwise prefer
    * the safe [[Observable!.multicast multicast]] operator.
    */
  def unsafeMulticast[B >: A, R](processor: Subject[B, R])(implicit s: Scheduler): ConnectableObservable[R] =
    ConnectableObservable.unsafeMulticast(this, processor)

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers).
    */
  def multicast[B >: A, R](pipe: Pipe[B, R])(implicit s: Scheduler): ConnectableObservable[R] =
    ConnectableObservable.multicast(this, pipe)

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[PublishSubject PublishSubject]].
    */
  def publish(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(PublishSubject[A]())

  /** Returns a new Observable that multi-casts (shares) the original
    * Observable.
    */
  def share(implicit s: Scheduler): Observable[A] =
    publish.refCount

  /** Caches the emissions from the source Observable and replays them
    * in order to any subsequent Subscribers. This operator has
    * similar behavior to [[Observable!.replay(implicit* replay]]
    * except that this auto-subscribes to the source Observable rather
    * than returning a
    * [[monix.streams.observables.ConnectableObservable ConnectableObservable]]
    * for which you must call
    * [[monix.streams.observables.ConnectableObservable.connect connect]]
    * to activate the subscription.
    *
    * When you call cache, it does not yet subscribe to the source
    * Observable and so does not yet begin caching items. This only
    * happens when the first Subscriber calls the resulting
    * Observable's `subscribe` method.
    *
    * Note: You sacrifice the ability to cancel the origin when you
    * use the cache operator so be careful not to use this on
    * Observables that emit an infinite or very large number of items
    * that will use up memory.
    *
    * @return an Observable that, when first subscribed to, caches all of its
    *         items and notifications for the benefit of subsequent subscribers
    */
  def cache: Observable[A] =
    CachedObservable.create(self)

  /** Caches the emissions from the source Observable and replays them
    * in order to any subsequent Subscribers. This operator has
    * similar behavior to [[Observable!.replay(implicit* replay]]
    * except that this auto-subscribes to the source Observable rather
    * than returning a
    * [[monix.streams.observables.ConnectableObservable ConnectableObservable]]
    * for which you must call
    * [[monix.streams.observables.ConnectableObservable.connect connect]]
    * to activate the subscription.
    *
    * When you call cache, it does not yet subscribe to the source
    * Observable and so does not yet begin caching items. This only
    * happens when the first Subscriber calls the resulting
    * Observable's `subscribe` method.
    *
    * @param maxCapacity is the maximum buffer size after which old events
    *        start being dropped (according to what happens when using
    *        [[ReplaySubject.createWithSize ReplaySubject.createWithSize]])
    *
    * @return an Observable that, when first subscribed to, caches all of its
    *         items and notifications for the benefit of subsequent subscribers
    */
  def cache(maxCapacity: Int): Observable[A] =
    CachedObservable.create(self, maxCapacity)

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[BehaviorSubject BehaviorSubject]].
    */
  def behavior[B >: A](initialValue: B)(implicit s: Scheduler): ConnectableObservable[B] =
    unsafeMulticast(BehaviorSubject[B](initialValue))

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.streams.subjects.ReplaySubject ReplaySubject]].
    */
  def replay(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(ReplaySubject[A]())

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.streams.subjects.ReplaySubject ReplaySubject]].
    *
    * @param bufferSize is the size of the buffer limiting the number
    *        of items that can be replayed (on overflow the head
    *        starts being dropped)
    */
  def replay(bufferSize: Int)(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(ReplaySubject.createWithSize[A](bufferSize))

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[AsyncSubject AsyncSubject]].
    */
  def publishLast(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(AsyncSubject[A]())

  /** Returns the first generated result as a Future and then cancels
    * the subscription.
    */
  def asFuture(implicit s: Scheduler): CancelableFuture[Option[A]] = {
    val promise = Promise[Option[A]]()
    val cancelable = head.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = s

      def onNext(elem: A) = {
        promise.trySuccess(Some(elem))
        Cancel
      }

      def onComplete() = {
        promise.trySuccess(None)
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
      }
    })

    val withTrigger = Cancelable { () =>
      try cancelable.cancel() finally
        promise.tryFailure(new CancellationException("CancelableFuture.cancel"))
    }

    CancelableFuture(promise.future, withTrigger)
  }

  /** Subscribes to the source `Observable` and foreach element emitted
    * by the source it executes the given callback.
    */
  def foreach(cb: A => Unit)(implicit s: Scheduler): Cancelable =
    unsafeSubscribeFn(new SyncSubscriber[A] {
      implicit val scheduler = s

      def onNext(elem: A): Ack =
        try {
          cb(elem)
          Continue
        } catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit =
        s.reportFailure(ex)
    })
}

object Observable {
  /** Observable constructor for creating an [[Observable]] from the
    * specified function.
    */
  def unsafeCreate[A](f: Subscriber[A] => Unit): Observable[A] = {
    new Observable[A] {
      def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable =
        try { f(subscriber); Cancelable.empty } catch {
          case NonFatal(ex) =>
            subscriber.scheduler.reportFailure(ex)
            Cancelable.empty
        }
    }
  }

  /** Given a factory of streams that can be observed, converts it
    * to an Observable, making sure to start execution on another
    * logical thread.
    */
  def apply[A, F[_] : CanObserve](fa: => F[A]): Observable[A] =
    fork(defer(fa))

  /** Creates an observable that doesn't emit anything, but immediately
    * calls `onComplete` instead.
    */
  def empty: Observable[Nothing] =
    builders.EmptyObservable

  /** Returns an `Observable` that on execution emits the given strict value.
    */
  def now[A](elem: A): Observable[A] =
    new builders.NowObservable(elem)

  /** Given a lazy by-name argument, converts it into an Observable
    * that emits a single element.
    */
  def eval[A](f: => A): Observable[A] =
    new builders.EvalObservable(f)

  /** Creates an Observable that emits an error.
    */
  def error(ex: Throwable): Observable[Nothing] =
    new builders.ErrorObservable(ex)

  /** Creates an Observable that doesn't emit anything and that never
    * completes.
    */
  def never: Observable[Nothing] =
    builders.NeverObservable

  /** Ensures that execution happens on a different logical thread. */
  def fork[A, F[_] : CanObserve](fa: F[A]): Observable[A] =
    new builders.ForkObservable(fa)

  /** Given a stream that can be observed, converts it to an Observable. */
  def from[A, F[_] : CanObserve](fa: F[A]): Observable[A] =
    implicitly[CanObserve[F]].observable(fa)

  /** Returns a new observable that creates a sequence from the
    * given factory on each subscription.
    */
  def defer[A, F[_] : CanObserve](factory: => F[A]): Observable[A] =
    new builders.DeferObservable(factory)

  /** Creates an Observable that emits auto-incremented natural numbers
    * (longs) spaced by a given time interval. Starts from 0 with no
    * delay, after which it emits incremented numbers spaced by the
    * `period` of time. The given `period` of time acts as a fixed
    * delay between successive events.
    *
    * @param delay the delay between 2 successive events
    */
  def intervalWithFixedDelay(delay: FiniteDuration): Observable[Long] =
    new builders.IntervalFixedDelayObservable(Duration.Zero, delay)

  /** Creates an Observable that emits auto-incremented natural numbers
    * (longs) spaced by a given time interval. Starts from 0 with no
    * delay, after which it emits incremented numbers spaced by the
    * `period` of time. The given `period` of time acts as a fixed
    * delay between successive events.
    *
    * @param initialDelay is the delay to wait before emitting the first event
    * @param delay the time to wait between 2 successive events
    */
  def intervalWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration): Observable[Long] =
    new builders.IntervalFixedDelayObservable(initialDelay, delay)

  /** Creates an Observable that emits auto-incremented natural numbers
    * (longs) spaced by a given time interval. Starts from 0 with no
    * delay, after which it emits incremented numbers spaced by the
    * `period` of time. The given `period` of time acts as a fixed
    * delay between successive events.
    *
    * @param delay the delay between 2 successive events
    */
  def interval(delay: FiniteDuration): Observable[Long] =
    intervalWithFixedDelay(delay)

  /** Creates an Observable that emits auto-incremented natural numbers
    * (longs) at a fixed rate, as given by the specified `period`. The
    * time it takes to process an `onNext` event gets subtracted from
    * the specified `period` and thus the created observable tries to
    * emit events spaced by the given time interval, regardless of how
    * long the processing of `onNext` takes.
    *
    * @param period the period between 2 successive `onNext` events
    */
  def intervalAtFixedRate(period: FiniteDuration): Observable[Long] =
    new builders.IntervalFixedRateObservable(Duration.Zero, period)

  /** Creates an Observable that emits auto-incremented natural numbers
    * (longs) at a fixed rate, as given by the specified `period`. The
    * time it takes to process an `onNext` event gets subtracted from
    * the specified `period` and thus the created observable tries to
    * emit events spaced by the given time interval, regardless of how
    * long the processing of `onNext` takes.
    *
    * This version of the `intervalAtFixedRate` allows specifying an
    * `initialDelay` before events start being emitted.
    *
    * @param initialDelay is the initial delay before emitting the first event
    * @param period the period between 2 successive `onNext` events
    */
  def intervalAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration): Observable[Long] =
    new builders.IntervalFixedRateObservable(initialDelay, period)

  /** Creates an Observable that continuously emits the given ''item'' repeatedly.
    */
  def repeat[A](elems: A*): Observable[A] =
    new builders.RepeatObservable(elems:_*)

  /** Repeats the execution of the given `task`, emitting
    * the results indefinitely.
    */
  def repeatEval[A](task: => A): Observable[A] =
    new builders.RepeatEvalObservable(task)

  /** Creates an Observable that emits items in the given range.
    *
    * @param from the range start
    * @param until the range end
    * @param step increment step, either positive or negative
    */
  def range(from: Long, until: Long, step: Long = 1L): Observable[Long] =
    new builders.RangeObservable(from, until, step)

  /** Given an initial state and a generator function that produces the
    * next state and the next element in the sequence, creates an
    * observable that keeps generating elements produced by our
    * generator function.
    */
  def fromStateAction[S, A](f: S => (A, S))(initialState: S): Observable[A] =
    new builders.StateActionObservable(initialState, f)

  /** Given a `org.reactivestreams.Publisher`, converts it into a
    * Monix / Rx Observable.
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    *
    * @see [[Observable!.toReactive]] for converting ``
    */
  def fromReactivePublisher[A](publisher: RPublisher[A]): Observable[A] =
    Observable.from(publisher)

  /** Wraps this Observable into a `org.reactivestreams.Publisher`.
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    */
  def toReactivePublisher[A](source: Observable[A])(implicit s: Scheduler): RPublisher[A] =
    source.toReactive[A](s)

  /** Create an Observable that repeatedly emits the given `item`, until
    * the underlying Observer cancels.
    */
  def timerRepeated[A](initialDelay: FiniteDuration, period: FiniteDuration, unit: A): Observable[A] =
    new builders.RepeatedValueObservable[A](initialDelay, period, unit)

  /** Concatenates the given list of ''observables'' into a single observable.
    */
  def flatten[A](sources: Observable[A]*): Observable[A] =
    Observable.from(sources).concat

  /** Concatenates the given list of ''observables'' into a single
    * observable.  Delays errors until the end.
    */
  def flattenDelayError[A](sources: Observable[A]*): Observable[A] =
    Observable.from(sources).concatDelayError

  /** Merges the given list of ''observables'' into a single observable.
    */
  def merge[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.from(sources).mergeMap(o => o)(os)

  /** Merges the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def mergeDelayError[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.from(sources).mergeMapDelayErrors(o => o)(os)

  /** Concatenates the given list of ''observables'' into a single
    * observable.
    */
  def concat[A, F[_] : CanObserve](sources: F[A]*): Observable[A] =
    Observable.from(sources).concatMapF[A,F](t => t)

  /** Concatenates the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def concatDelayError[A, F[_] : CanObserve](sources: F[A]*): Observable[A] =
    Observable.from(sources).concatMapDelayErrorF[A,F](t => t)

  /** Creates a new observable from two observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatest2]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zip2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2])(f: (A1,A2) => R): Observable[R] =
    new builders.Zip2Observable[A1,A2,R](oa1,oa2)(f)

  /** Creates a new observable from three observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatest3]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zip3[A1,A2,A3,R](oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3])
    (f: (A1,A2,A3) => R): Observable[R] =
    new builders.Zip3Observable(oa1,oa2,oa3)(f)

  /** Creates a new observable from four observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatest4]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zip4[A1,A2,A3,A4,R]
    (oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3], oa4: Observable[A4])
    (f: (A1,A2,A3,A4) => R): Observable[R] =
    new builders.Zip4Observable(oa1,oa2,oa3,oa4)(f)

  /** Creates a new observable from five observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatest5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zip5[A1,A2,A3,A4,A5,R]
    (oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
     oa4: Observable[A4], oa5: Observable[A5])
    (f: (A1,A2,A3,A4,A5) => R): Observable[R] =
    new builders.Zip5Observable(oa1,oa2,oa3,oa4,oa5)(f)

  /** Creates a new observable from five observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatest5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zip6[A1,A2,A3,A4,A5,A6,R]
    (oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
     oa4: Observable[A4], oa5: Observable[A5], oa6: Observable[A6])
    (f: (A1,A2,A3,A4,A5,A6) => R): Observable[R] =
    new builders.Zip6Observable(oa1,oa2,oa3,oa4,oa5,oa6)(f)

  /** Given an observable sequence, it [[Observable!.zip zips]] them
    * together returning a new observable that generates sequences.
    */
  def zipList[A](sources: Observable[A]*): Observable[Seq[A]] = {
    if (sources.isEmpty) Observable.empty
    else {
      val seed = sources.head.map(t => Vector(t))
      sources.tail.foldLeft(seed) { (acc, obs) =>
        acc.zipWith(obs)((seq, elem) => seq :+ elem)
      }
    }
  }

  /** Creates a combined observable from 2 source observables.
    *
    * This operator behaves in a similar way to [[zip2]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest2[A1,A2,R](a1: Observable[A1], a2: Observable[A2])
    (f: (A1,A2) => R): Observable[R] =
    new builders.CombineLatest2Observable[A1,A2,R](a1,a2)(f)

  /** Creates a combined observable from 3 source observables.
    *
    * This operator behaves in a similar way to [[zip3]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest3[A1,A2,A3,R](a1: Observable[A1], a2: Observable[A2], a3: Observable[A3])
    (f: (A1,A2,A3) => R): Observable[R] =
    new builders.CombineLatest3Observable[A1,A2,A3,R](a1,a2,a3)(f)

  /** Creates a combined observable from 4 source observables.
    *
    * This operator behaves in a similar way to [[zip4]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest4[A1,A2,A3,A4,R]
    (a1: Observable[A1], a2: Observable[A2], a3: Observable[A3], a4: Observable[A4])
    (f: (A1,A2,A3,A4) => R): Observable[R] =
    new builders.CombineLatest4Observable[A1,A2,A3,A4,R](a1,a2,a3,a4)(f)

  /** Creates a combined observable from 5 source observables.
    *
    * This operator behaves in a similar way to [[zip5]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest5[A1,A2,A3,A4,A5,R]
    (a1: Observable[A1], a2: Observable[A2], a3: Observable[A3], a4: Observable[A4], a5: Observable[A5])
    (f: (A1,A2,A3,A4,A5) => R): Observable[R] =
    new builders.CombineLatest5Observable[A1,A2,A3,A4,A5,R](a1,a2,a3,a4,a5)(f)

  /** Creates a combined observable from 6 source observables.
    *
    * This operator behaves in a similar way to [[zip6]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest6[A1,A2,A3,A4,A5,A6,R]
    (a1: Observable[A1], a2: Observable[A2], a3: Observable[A3],
     a4: Observable[A4], a5: Observable[A5], a6: Observable[A6])
    (f: (A1,A2,A3,A4,A5,A6) => R): Observable[R] =
    new builders.CombineLatest6Observable[A1,A2,A3,A4,A5,A6,R](a1,a2,a3,a4,a5,a6)(f)

  /** Given an observable sequence, it combines them together
    * (using [[combineLatest2 combineLatest]])
    * returning a new observable that generates sequences.
    */
  def combineLatestList[A](sources: Observable[A]*): Observable[Seq[A]] = {
    if (sources.isEmpty) Observable.empty
    else {
      val seed = sources.head.map(t => Vector(t))
      sources.tail.foldLeft(seed) { (acc, obs) =>
        acc.combineLatestWith(obs) { (seq, elem) => seq :+ elem }
      }
    }
  }

  /** Given a list of source Observables, emits all of the items from
    * the first of these Observables to emit an item and cancel the
    * rest.
    */
  def amb[A](source: Observable[A]*): Observable[A] =
    new builders.FirstStartedObservable(source: _*)

  /** Implicit conversion from Observable to Publisher.
    */
  implicit def ObservableIsReactive[A](source: Observable[A])
    (implicit s: Scheduler): RPublisher[A] =
    source.toReactive

  /** Observables can be converted to Tasks. */

}
