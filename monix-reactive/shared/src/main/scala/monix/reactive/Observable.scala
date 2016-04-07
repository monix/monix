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

import monix.eval.{Task, TaskEnumerator}
import monix.execution.Ack.{Continue, Stop}
import monix.execution._
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.internal.builders
import monix.reactive.internal.builders.ObservableToTaskEnumerator
import monix.reactive.observables.ObservableLike.{Operator, Transformer}
import monix.reactive.observables._
import monix.reactive.observers._
import monix.reactive.subjects._
import monix.types.Asynchronous
import org.reactivestreams.{Publisher => RPublisher, Subscriber => RSubscriber}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal

/** The Observable type that implements the Reactive Pattern.
  *
  * Provides methods of subscribing to the Observable and operators
  * for combining observable sources, filtering, modifying,
  * throttling, buffering, error handling and others.
  *
  * See the available documentation at: [[https://monix.io]]
  */
trait Observable[+A] extends ObservableLike[A, Observable] { self =>
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
  override def liftByOperator[B](operator: Operator[A, B]): Observable[B] =
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
  def toReactivePublisher[B >: A](implicit s: Scheduler): RPublisher[B] =
    new RPublisher[B] {
      def subscribe(subscriber: RSubscriber[_ >: B]): Unit = {
        val subscription = SingleAssignmentCancelable()
        subscription := unsafeSubscribeFn(SafeSubscriber(
          Subscriber.fromReactiveSubscriber(subscriber, subscription)
        ))
      }
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
    * [[monix.reactive.subjects.PublishSubject PublishSubject]].
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
    * [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
    * for which you must call
    * [[monix.reactive.observables.ConnectableObservable.connect connect]]
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
    * [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
    * for which you must call
    * [[monix.reactive.observables.ConnectableObservable.connect connect]]
    * to activate the subscription.
    *
    * When you call cache, it does not yet subscribe to the source
    * Observable and so does not yet begin caching items. This only
    * happens when the first Subscriber calls the resulting
    * Observable's `subscribe` method.
    *
    * @param maxCapacity is the maximum buffer size after which old events
    *        start being dropped (according to what happens when using
    *        [[monix.reactive.subjects.ReplaySubject.createWithSize ReplaySubject.createWithSize]])
    * @return an Observable that, when first subscribed to, caches all of its
    *         items and notifications for the benefit of subsequent subscribers
    */
  def cache(maxCapacity: Int): Observable[A] =
    CachedObservable.create(self, maxCapacity)

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.reactive.subjects.BehaviorSubject BehaviorSubject]].
    */
  def behavior[B >: A](initialValue: B)(implicit s: Scheduler): ConnectableObservable[B] =
    unsafeMulticast(BehaviorSubject[B](initialValue))

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.reactive.subjects.ReplaySubject ReplaySubject]].
    */
  def replay(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(ReplaySubject[A]())

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.reactive.subjects.ReplaySubject ReplaySubject]].
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
    * [[monix.reactive.subjects.AsyncSubject AsyncSubject]].
    */
  def publishLast(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(AsyncSubject[A]())

  /** Creates a new [[CancelableFuture CancelableFuture]]
    * that upon execution will signal the last generated element of the
    * source observable. Returns an `Option` because the source can be empty.
    */
  def runAsyncGetFirst(implicit s: Scheduler): CancelableFuture[Option[A]] =
    firstL.runAsync(s)

  /** Creates a new [[monix.execution.CancelableFuture CancelableFuture]]
    * that upon execution will signal the last generated element of the
    * source observable. Returns an `Option` because the source can be empty.
    */
  def runAsyncGetLast(implicit s: Scheduler): CancelableFuture[Option[A]] =
    lastL.runAsync(s)

  /** Creates a new [[monix.eval.Task Task]] that upon execution
    * will signal the first generated element of the source observable.
    * Returns an `Option` because the source can be empty.
    */
  def firstL: Task[Option[A]] =
    Task.unsafeCreate { (s, c, cb) =>
      val task = SingleAssignmentCancelable()
      c push task

      task := unsafeSubscribeFn(new SyncSubscriber[A] {
        implicit val scheduler: Scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Stop = {
          c.pop()
          cb.onSuccess(Some(elem))
          isDone = true
          Stop
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; c.pop(); cb.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; c.pop(); cb.onSuccess(None) }
      })
    }

  /** Returns a [[monix.eval.Task Task]] that upon execution
    * will signal the last generated element of the source observable.
    * Returns an `Option` because the source can be empty.
    */
  def lastL: Task[Option[A]] =
    Task.unsafeCreate { (s, c, cb) =>
      val task = SingleAssignmentCancelable()
      c push task

      task := unsafeSubscribeFn(new SyncSubscriber[A] {
        implicit val scheduler: Scheduler = s
        private[this] var value: A = _
        private[this] var isEmpty = true

        def onNext(elem: A): Continue = {
          if (isEmpty) isEmpty = false
          value = elem
          Continue
        }

        def onError(ex: Throwable): Unit = {
          c.pop()
          cb.onError(ex)
        }

        def onComplete(): Unit = {
          c.pop()
          cb.onSuccess(if (isEmpty) None else Some(value))
        }
      })
    }

  /** Creates a new [[monix.eval.Task Task]] that will consume the
    * source observable and upon completion of the source it will
    * complete with `Unit`.
    */
  def completedL: Task[Unit] =
    Task.unsafeCreate { (s, c, cb) =>
      val task = SingleAssignmentCancelable()
      c push task

      task := unsafeSubscribeFn(new SyncSubscriber[A] {
        implicit val scheduler: Scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Continue = Continue
        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; c.pop(); cb.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; c.pop(); cb.onSuccess(()) }
      })
    }

  /** Builds an [[monix.eval.TaskEnumerator TaskEnumerator]] from the
    * source observable.
    */
  def taskEnumerator(batchSize: Int): Task[TaskEnumerator[A]] =
    ObservableToTaskEnumerator(self, batchSize)

  /** Creates a new [[monix.eval.Task Task]] that will consume the
    * source observable, executing the given callback for each element.
    */
  def foreachL(cb: A => Unit): Task[Unit] =
    Task.unsafeCreate { (s, c, onFinish) =>
      val task = SingleAssignmentCancelable()
      c push task

      task := unsafeSubscribeFn(new SyncSubscriber[A] {
        implicit val scheduler: Scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          try {
            cb(elem)
            Continue
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; c.pop(); onFinish.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; c.pop(); onFinish.onSuccess(()) }
      })
    }

  /** Subscribes to the source `Observable` and foreach element emitted
    * by the source it executes the given callback.
    */
  def foreach(cb: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] =
    foreachL(cb).runAsync
}

object Observable {
  /** Creates an observable that doesn't emit anything, but immediately
    * calls `onComplete` instead.
    */
  def empty[A]: Observable[A] =
    builders.EmptyObservable

  /** Returns an `Observable` that on execution emits the given strict value.
    */
  def now[A](elem: A): Observable[A] =
    new builders.NowObservable(elem)

  /** Given a non-strict value, converts it into an Observable
    * that emits a single element.
    */
  def evalAlways[A](a: => A): Observable[A] =
    new builders.EvalAlwaysObservable(a)

  /** Given a non-strict value, converts it into an Observable
    * that emits a single element and that memoizes the value
    * for subsequent invocations.
    */
  def evalOnce[A](f: => A): Observable[A] =
    new builders.EvalOnceObservable(f)

  /** Lifts a non-strict value into an observable that emits a single element,
    * but upon subscription delay its evaluation by the specified timespan
    */
  def evalDelayed[A](delay: FiniteDuration, a: => A): Observable[A] =
    evalAlways(a).delaySubscription(delay)

  /** Creates an Observable that emits an error.
    */
  def error(ex: Throwable): Observable[Nothing] =
    new builders.ErrorObservable(ex)

  /** Creates an Observable that doesn't emit anything and that never
    * completes.
    */
  def never: Observable[Nothing] =
    builders.NeverObservable

  /** Forks a logical thread on executing the subscription. */
  def fork[A](fa: Observable[A]): Observable[A] =
    new builders.ForkObservable(fa)

  /** Given a subscribe function, lifts it into an [[Observable]].
    *
    * This function is unsafe to use because users have to know and apply
    * the Monix communication contract, related to thread-safety, communicating
    * demand (back-pressure) and error handling.
    *
    * Only use if you know what you're doing. Otherwise prefer [[create]].
    */
  def unsafeCreate[A](f: Subscriber[A] => Cancelable): Observable[A] =
    new builders.UnsafeCreateObservable(f)

  /** Creates an observable from a function that receives a
    * concurrent and safe [[SyncSubscriber]].
    *
    * This builder represents the safe way of building observables
    * from data-sources that cannot be back-pressured.
    */
  def create[A](overflowStrategy: OverflowStrategy.Synchronous[A])
    (f: SyncSubscriber[A] => Cancelable): Observable[A] =
    new builders.CreateObservable(overflowStrategy, f)

  /** Creates an input channel and an output observable pair for
    * building a [[MulticastStrategy multicast]] data-source.
    *
    * Useful for building [[MulticastStrategy multicast]] observables
    * from data-sources that cannot be back-pressured.
    *
    * Prefer [[create]] when possible.
    */
  def multicast[A](multicast: MulticastStrategy[A], overflow: OverflowStrategy.Synchronous[A])
    (implicit s: Scheduler): (SyncObserver[A], Observable[A]) = {

    val ref = ConcurrentSubject(multicast, overflow)
    (ref, ref)
  }

  /** Converts any `Iterator` into an [[Observable]]. */
  def fromIterator[A](iterator: Iterator[A]): Observable[A] =
    new builders.IteratorAsObservable[A](iterator)

  /** Converts any `Iterable` into an [[Observable]]. */
  def fromIterable[A](iterable: Iterable[A]): Observable[A] =
    new builders.IterableAsObservable[A](iterable)

  /** Given a `org.reactivestreams.Publisher`, converts it into a
    * Monix / Rx Observable.
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    *
    * @see [[Observable.toReactive]] for converting an `Observable` to
    *      a reactive publisher.
    */
  def fromReactivePublisher[A](publisher: RPublisher[A]): Observable[A] =
    new builders.ReactiveObservable[A](publisher)

  /** Converts a Scala `Future` provided into an [[Observable]].
    *
    * If the created instance is a
    * [[monix.execution.CancelableFuture CancelableFuture]],
    * then it will be used for the returned
    * [[monix.execution.Cancelable Cancelable]] on `subscribe`.
    */
  def fromFuture[A](factory: => Future[A]): Observable[A] =
    new builders.FutureAsObservable(factory)

  /** Converts any [[monix.eval.Task Task]] into an [[Observable]]. */
  def fromTask[A](task: Task[A]): Observable[A] =
    new builders.TaskAsObservable(task)

  /** Returns a new observable that creates a sequence from the
    * given factory on each subscription.
    */
  def defer[A](factory: => Observable[A]): Observable[A] =
    new builders.DeferObservable(factory)

  /** Builds a new observable from a strict `head` and a lazily
    * evaluated head.
    */
  def cons[A](head: A, tail: Observable[A]): Observable[A] =
    new builders.ConsObservable[A](head, tail)

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

  /** Wraps this Observable into a `org.reactivestreams.Publisher`.
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    */
  def toReactive[A](source: Observable[A])(implicit s: Scheduler): RPublisher[A] =
    source.toReactivePublisher[A](s)

  /** Create an Observable that repeatedly emits the given `item`, until
    * the underlying Observer cancels.
    */
  def timerRepeated[A](initialDelay: FiniteDuration, period: FiniteDuration, unit: A): Observable[A] =
    new builders.RepeatedValueObservable[A](initialDelay, period, unit)

  /** Concatenates the given list of ''observables'' into a single observable.
    */
  def flatten[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concat

  /** Concatenates the given list of ''observables'' into a single
    * observable.  Delays errors until the end.
    */
  def flattenDelayError[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concatDelayError

  /** Merges the given list of ''observables'' into a single observable.
    */
  def merge[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.fromIterable(sources).mergeMap(o => o)(os)

  /** Merges the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def mergeDelayError[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.fromIterable(sources).mergeMapDelayErrors(o => o)(os)

  /** Concatenates the given list of ''observables'' into a single
    * observable.
    */
  def concat[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concatMap[A](t => t)

  /** Concatenates the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def concatDelayError[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concatMapDelayError[A](t => t)

  /** Creates a new observable from two observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith2]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    */
  def zip2[A1,A2](oa1: Observable[A1], oa2: Observable[A2]): Observable[(A1,A2)] =
    new builders.Zip2Observable[A1,A2,(A1,A2)](oa1,oa2)((a1,a2) => (a1,a2))

  /** Creates a new observable from two observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith2]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipWith2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2])(f: (A1,A2) => R): Observable[R] =
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
    * See [[combineLatestWith3]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    */
  def zip3[A1,A2,A3](oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3]): Observable[(A1,A2,A3)] =
    new builders.Zip3Observable(oa1,oa2,oa3)((a1,a2,a3) => (a1,a2,a3))

  /** Creates a new observable from three observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith3]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipWith3[A1,A2,A3,R](oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3])
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
    * See [[combineLatestWith4]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    */
  def zip4[A1,A2,A3,A4]
    (oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3], oa4: Observable[A4]): Observable[(A1,A2,A3,A4)] =
    new builders.Zip4Observable(oa1,oa2,oa3,oa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))

  /** Creates a new observable from four observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith4]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipWith4[A1,A2,A3,A4,R]
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
    * See [[combineLatestWith5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    */
  def zip5[A1,A2,A3,A4,A5](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
    oa4: Observable[A4], oa5: Observable[A5]): Observable[(A1,A2,A3,A4,A5)] =
    new builders.Zip5Observable(oa1,oa2,oa3,oa4,oa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  /** Creates a new observable from five observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipWith5[A1,A2,A3,A4,A5,R]
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
    * See [[combineLatestWith5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    */
  def zip6[A1,A2,A3,A4,A5,A6](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
    oa4: Observable[A4], oa5: Observable[A5], oa6: Observable[A6]): Observable[(A1,A2,A3,A4,A5,A6)] =
    new builders.Zip6Observable(oa1,oa2,oa3,oa4,oa5,oa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Creates a new observable from five observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestWith5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipWith6[A1,A2,A3,A4,A5,A6,R]
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
  def combineLatest2[A1,A2](oa1: Observable[A1], oa2: Observable[A2]): Observable[(A1, A2)] =
    new builders.CombineLatest2Observable[A1,A2,(A1,A2)](oa1,oa2)((a1,a2) => (a1,a2))

  /** Creates a combined observable from 2 source observables.
    *
    * This operator behaves in a similar way to [[zipWith2]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestWith2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2])
    (f: (A1,A2) => R): Observable[R] =
    new builders.CombineLatest2Observable[A1,A2,R](oa1,oa2)(f)

  /** Creates a combined observable from 3 source observables.
    *
    * This operator behaves in a similar way to [[zip3]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatest3[A1,A2,A3](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3]): Observable[(A1,A2,A3)] =
    new builders.CombineLatest3Observable(oa1,oa2,oa3)((a1,a2,a3) => (a1,a2,a3))

  /** Creates a combined observable from 3 source observables.
    *
    * This operator behaves in a similar way to [[zipWith3]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestWith3[A1,A2,A3,R](a1: Observable[A1], a2: Observable[A2], a3: Observable[A3])
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
  def combineLatest4[A1,A2,A3,A4](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
    oa4: Observable[A4]): Observable[(A1,A2,A3,A4)] =
    new builders.CombineLatest4Observable(oa1,oa2,oa3,oa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))

  /** Creates a combined observable from 4 source observables.
    *
    * This operator behaves in a similar way to [[zipWith4]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestWith4[A1,A2,A3,A4,R]
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
  def combineLatest5[A1,A2,A3,A4,A5](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
    oa4: Observable[A4], oa5: Observable[A5]): Observable[(A1,A2,A3,A4,A5)] =
    new builders.CombineLatest5Observable(oa1,oa2,oa3,oa4,oa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  /** Creates a combined observable from 5 source observables.
    *
    * This operator behaves in a similar way to [[zipWith5]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestWith5[A1,A2,A3,A4,A5,R]
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
  def combineLatest6[A1,A2,A3,A4,A5,A6](
    oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3],
    oa4: Observable[A4], oa5: Observable[A5], oa6: Observable[A6]): Observable[(A1,A2,A3,A4,A5,A6)] =
    new builders.CombineLatest6Observable(oa1,oa2,oa3,oa4,oa5,oa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Creates a combined observable from 6 source observables.
    *
    * This operator behaves in a similar way to [[zipWith6]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestWith6[A1,A2,A3,A4,A5,A6,R]
    (a1: Observable[A1], a2: Observable[A2], a3: Observable[A3],
     a4: Observable[A4], a5: Observable[A5], a6: Observable[A6])
    (f: (A1,A2,A3,A4,A5,A6) => R): Observable[R] =
    new builders.CombineLatest6Observable[A1,A2,A3,A4,A5,A6,R](a1,a2,a3,a4,a5,a6)(f)

  /** Given an observable sequence, it combines them together
    * (using [[combineLatestWith2 combineLatest]])
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
    * the first of these Observables to emit an item or to complete,
    * and cancel the rest.
    */
  def firstStartedOf[A](source: Observable[A]*): Observable[A] =
    new builders.FirstStartedObservable(source: _*)

  /** Type-class instances for [[Observable]]. */
  implicit val instances: Asynchronous[Observable] =
    new Asynchronous[Observable] {
      override def error[A](e: Throwable): Observable[A] =
        Observable.error(e)
      override def point[A](x: A): Observable[A] =
        Observable.now(x)
      override def delayedEval[A](delay: FiniteDuration, a: => A): Observable[A] =
        Observable.evalAlways(a)

      override def now[A](a: A): Observable[A] =
        Observable.now(a)
      override def evalAlways[A](a: => A): Observable[A] =
        Observable.evalAlways(a)
      override def evalOnce[A](a: => A): Observable[A] =
        Observable.evalOnce(a)
      override def memoize[A](fa: Observable[A]): Observable[A] =
        fa.cache
      override def defer[A](fa: => Observable[A]): Observable[A] =
        Observable.defer(fa)

      override def onErrorHandleWith[A](fa: Observable[A])(f: Throwable => Observable[A]): Observable[A] =
        fa.onErrorHandleWith(f)
      override def onErrorHandle[A](fa: Observable[A])(f: Throwable => A): Observable[A] =
        fa.onErrorHandle(f)
      override def onErrorFallbackTo[A](fa: Observable[A], other: Observable[A]): Observable[A] =
        fa.onErrorFallbackTo(other)
      override def onErrorRetry[A](fa: Observable[A], maxRetries: Long): Observable[A] =
        fa.onErrorRetry(maxRetries)
      override def onErrorRetryIf[A](fa: Observable[A])(p: (Throwable) => Boolean): Observable[A] =
        fa.onErrorRetryIf(p)
      override def onErrorRecoverWith[A](fa: Observable[A])(pf: PartialFunction[Throwable, Observable[A]]): Observable[A] =
        fa.onErrorRecoverWith(pf)
      override def onErrorRecover[A](fa: Observable[A])(pf: PartialFunction[Throwable, A]): Observable[A] =
        fa.onErrorRecover(pf)
      override def failed[A](fa: Observable[A]): Observable[Throwable] =
        fa.failed
      override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
        fa.map(f)

      override def zip2[A1, A2](fa1: Observable[A1], fa2: Observable[A2]): Observable[(A1, A2)] =
        Observable.zip2(fa1, fa2)
      override def zipWith2[A1, A2, R](fa1: Observable[A1], fa2: Observable[A2])(f: (A1, A2) => R): Observable[R] =
        Observable.zipWith2(fa1, fa2)(f)
      override def zip3[A1, A2, A3](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3]): Observable[(A1, A2, A3)] =
        Observable.zip3(fa1,fa2,fa3)
      override def zipWith3[A1, A2, A3, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3])(f: (A1, A2, A3) => R): Observable[R] =
        Observable.zipWith3(fa1,fa2,fa3)(f)
      override def zip4[A1, A2, A3, A4](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4]): Observable[(A1, A2, A3, A4)] =
        Observable.zip4(fa1,fa2,fa3,fa4)
      override def zipWith4[A1, A2, A3, A4, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4])(f: (A1, A2, A3, A4) => R): Observable[R] =
        Observable.zipWith4(fa1,fa2,fa3,fa4)(f)
      override def zip5[A1, A2, A3, A4, A5](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5]): Observable[(A1, A2, A3, A4, A5)] =
        Observable.zip5(fa1,fa2,fa3,fa4,fa5)
      override def zipWith5[A1, A2, A3, A4, A5, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5])(f: (A1, A2, A3, A4, A5) => R): Observable[R] =
        Observable.zipWith5(fa1,fa2,fa3,fa4,fa5)(f)
      override def zip6[A1, A2, A3, A4, A5, A6](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5], fa6: Observable[A6]): Observable[(A1, A2, A3, A4, A5, A6)] =
        Observable.zip6(fa1,fa2,fa3,fa4,fa5,fa6)
      override def zipWith6[A1, A2, A3, A4, A5, A6, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5], fa6: Observable[A6])(f: (A1, A2, A3, A4, A5, A6) => R): Observable[R] =
        Observable.zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)(f)
      override def zipList[A](sources: Seq[Observable[A]]): Observable[Seq[A]] =
        Observable.zipList(sources:_*)

      override def delayExecution[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.delaySubscription(timespan)
      override def delayExecutionWith[A, B](fa: Observable[A], trigger: Observable[B]): Observable[A] =
        fa.delaySubscriptionWith(trigger)
      override def delayResult[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.delayOnNext(timespan)
      override def delayResultBySelector[A, B](fa: Observable[A])(selector: (A) => Observable[B]): Observable[A] =
        fa.delayOnNextBySelector(selector)

      override def timeout[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.timeoutOnSlowUpstream(timespan)
      override def timeoutTo[A](fa: Observable[A], timespan: FiniteDuration, backup: Observable[A]): Observable[A] =
        fa.timeoutOnSlowUpstreamTo(timespan, backup)

      override def chooseFirstOf[A](seq: Seq[Observable[A]]): Observable[A] =
        Observable.firstStartedOf(seq:_*)
      override def unit: Observable[Unit] =
        Observable.now(())
      override def flatten[A](ffa: Observable[Observable[A]]): Observable[A] =
        ffa.flatten
      override def flatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
        fa.flatMap(f)
    }
}
