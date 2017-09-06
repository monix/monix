/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import java.io.{BufferedReader, InputStream, Reader}

import cats.{CoflatMap, MonadError, Monoid, MonoidK, Order}
import monix.eval.Coeval.Eager
import monix.eval.{Callback, Coeval, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution._
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.eval.Coeval
import monix.reactive.internal.builders
import monix.reactive.internal.subscribers.ForeachSubscriber
import monix.reactive.observables.ObservableLike.{Operator, Transformer}
import monix.reactive.observables._
import monix.reactive.observers._
import monix.reactive.subjects._
import org.reactivestreams.{Publisher => RPublisher, Subscriber => RSubscriber}

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Future, Promise}

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
    * @see [[consumeWith]] for another way of consuming observables
    */
  def subscribe(subscriber: Subscriber[A]): Cancelable = {
    unsafeSubscribeFn(SafeSubscriber[A](subscriber))
  }

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    * @see [[consumeWith]] for another way of consuming observables
    */
  def subscribe(observer: Observer[A])(implicit s: Scheduler): Cancelable =
    subscribe(Subscriber(observer, s))

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    * @see [[consumeWith]] for another way of consuming observables
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
    * @see [[consumeWith]] for another way of consuming observables
    */
  def subscribe(nextFn: A => Future[Ack], errorFn: Throwable => Unit)(implicit s: Scheduler): Cancelable =
    subscribe(nextFn, errorFn, () => ())

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    * @see [[consumeWith]] for another way of consuming observables
    */
  def subscribe()(implicit s: Scheduler): Cancelable =
    subscribe(_ => Continue)

  /** Subscribes to the stream.
    *
    * @return a subscription that can be used to cancel the streaming.
    * @see [[consumeWith]] for another way of consuming observables
    */
  def subscribe(nextFn: A => Future[Ack])(implicit s: Scheduler): Cancelable =
    subscribe(nextFn, error => s.reportFailure(error), () => ())

  /** On execution, consumes the source observable
    * with the given [[Consumer]], effectively transforming the
    * source observable into a [[monix.eval.Task Task]].
    */
  def consumeWith[R](f: Consumer[A,R]): Task[R] =
    f(self)

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

  /** Converts this `Observable` into an `org.reactivestreams.Publisher`.
    *
    * Meant for interoperability with other Reactive Streams
    * implementations.
    *
    * Usage sample:
    *
    * {{{
    *   import monix.eval.Task
    *   import monix.execution.rstreams.SingleAssignmentSubscription
    *   import org.reactivestreams.{Publisher, Subscriber, Subscription}
    *
    *   def sum(source: Publisher[Int], requestSize: Int): Task[Long] =
    *     Task.create { (_, cb) =>
    *       val sub = SingleAssignmentSubscription()
    *
    *       source.subscribe(new Subscriber[Int] {
    *         private[this] var requested = 0L
    *         private[this] var sum = 0L
    *
    *         def onSubscribe(s: Subscription): Unit = {
    *           sub := s
    *           requested = requestSize
    *           s.request(requestSize)
    *         }
    *
    *         def onNext(t: Int): Unit = {
    *           sum += t
    *           if (requestSize != Long.MaxValue) requested -= 1
    *
    *           if (requested <= 0) {
    *             requested = requestSize
    *             sub.request(request)
    *           }
    *         }
    *
    *         def onError(t: Throwable): Unit =
    *           cb.onError(t)
    *         def onComplete(): Unit =
    *           cb.onSuccess(sum)
    *       })
    *
    *       // Cancelable that can be used by Task
    *       sub
    *     }
    *
    *   val pub = Observable(1, 2, 3, 4).toReactivePublisher
    *
    *   // Yields 10
    *   sum(pub, requestSize = 128)
    * }}}
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol for details.
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
    *        [[monix.reactive.subjects.ReplaySubject.createLimited[A](capacity:Int,initial* ReplaySubject.createLimited]])
    *
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
    unsafeMulticast(ReplaySubject.createLimited[A](bufferSize))

  /** Converts this observable into a multicast observable, useful for
    * turning a cold observable into a hot one (i.e. whose source is
    * shared by all observers). The underlying subject used is a
    * [[monix.reactive.subjects.AsyncSubject AsyncSubject]].
    */
  def publishLast(implicit s: Scheduler): ConnectableObservable[A] =
    unsafeMulticast(AsyncSubject[A]())

  /** Creates a new [[monix.execution.CancelableFuture CancelableFuture]]
    * that upon execution will signal the last generated element of the
    * source observable. Returns an `Option` because the source can be empty.
    */
  def runAsyncGetFirst(implicit s: Scheduler): CancelableFuture[Option[A]] =
    firstOptionL.runAsync(s)

  /** Creates a new [[monix.execution.CancelableFuture CancelableFuture]]
    * that upon execution will signal the last generated element of the
    * source observable. Returns an `Option` because the source can be empty.
    */
  def runAsyncGetLast(implicit s: Scheduler): CancelableFuture[Option[A]] =
    lastOptionL.runAsync(s)

  /** Creates a task that emits the total number of `onNext`
    * events that were emitted by the source.
    */
  def countL: Task[Long] =
    countF.headL

  /** Returns a `Task` which emits either `true`, in case the given predicate
    * holds for at least one item, or `false` otherwise.
    *
    * @param p is a function that evaluates the items emitted by the
    *        source, returning `true` if they pass the filter
    * @return a task that emits `true` or `false` in case
    *         the given predicate holds or not for at least one item
    */
  def existsL(p: A => Boolean): Task[Boolean] =
    findF(p).foldLeftL(false)((_, _) => true)

  /** Returns a task which emits the first item for which
    * the predicate holds.
    *
    * @param p is a function that evaluates the items emitted by the
    *        source observable, returning `true` if they pass the filter
    * @return a task that emits the first item in the source
    *         observable for which the filter evaluates as `true`
    */
  def findL(p: A => Boolean): Task[Option[A]] =
    findF(p).headOptionL

  /** Given evidence that type `A` has a `cats.Monoid` implementation,
    * folds the stream with the provided monoid definition.
    *
    * For streams emitting numbers, this effectively sums them up.
    * For strings, this concatenates them.
    *
    * Example:
    *
    * {{{
    *   // Yields 10
    *   Observable(1, 2, 3, 4).foldL
    *
    *   // Yields "1234"
    *   Observable("1", "2", "3", "4").foldL
    * }}}
    *
    * Note, in case you don't have a `Monoid` instance in scope,
    * but you feel like you should, try this import:
    *
    * {{{
    *   import cats.instances.all._
    * }}}
    *
    * @see [[foldF]] for the version that returns an observable
    *      instead of a task.
    *
    * @param A is the `cats.Monoid` type class instance that's needed
    *          in scope for folding the source
    *
    * @return the result of combining all elements of the source,
    *         or the defined `Monoid.empty` element in case the
    *         stream is empty
    */
  def foldL[AA >: A](implicit A: Monoid[AA]): Task[AA] =
    foldF(A).headL

  /** Applies a binary operator to a start value and all elements of
    * the source, going left to right and returns a new `Task` that
    * upon evaluation will eventually emit the final result.
    */
  def foldLeftL[R](seed: => R)(op: (R, A) => R): Task[R] =
    foldLeftF(seed)(op).headL

  /** Folds the source observable, from start to finish, until the
    * source completes, or until the operator short-circuits the
    * process by returning `false`.
    *
    * Note that a call to [[foldLeftL]] is equivalent to this function
    * being called with an operator always returning `Left` results.
    *
    * Example: {{{
    *   // Sums first 10 items
    *   Observable.range(0, 1000).foldWhileLeftL((0, 0)) {
    *     case ((sum, count), e) =>
    *       val next = (sum + e, count + 1)
    *       if (count + 1 < 10) Left(next) else Right(next)
    *   }
    *
    *   // Implements exists(predicate)
    *   Observable(1, 2, 3, 4, 5).foldWhileLeftL(false) {
    *     (default, e) =>
    *       if (e == 3) Right(true) else Left(default)
    *   }
    *
    *   // Implements forall(predicate)
    *   Observable(1, 2, 3, 4, 5).foldWhileLeftL(true) {
    *     (default, e) =>
    *       if (e != 3) Right(false) else Left(default)
    *   }
    * }}}
    *
    * @see [[foldWhileLeftF]] for a version that returns an observable
    *      instead of a task.
    *
    * @param seed is the initial state, specified as a possibly lazy value;
    *        it gets evaluated when the subscription happens and if it
    *        triggers an error then the subscriber will get immediately
    *        terminated with an error
    *
    * @param op is the binary operator returning either `Left`,
    *        signaling that the state should be evolved or a `Right`,
    *        signaling that the process can be short-circuited and
    *        the result returned immediately
    *
    * @return the result of inserting `op` between consecutive
    *         elements of this observable, going from left to right with
    *         the `seed` as the start value, or `seed` if the observable
    *         is empty
    */
  def foldWhileLeftL[S](seed: => S)(op: (S, A) => Either[S, S]): Task[S] =
    foldWhileLeftF(seed)(op).headL

  /** Returns a `Task` that emits a single boolean, either true, in
    * case the given predicate holds for all the items emitted by the
    * source, or false in case at least one item is not verifying the
    * given predicate.
    *
    * @param p is a function that evaluates the items emitted by the source
    *        observable, returning `true` if they pass the filter
    * @return a task that emits only true or false in case the given
    *         predicate holds or not for all the items
    */
  def forAllL(p: A => Boolean): Task[Boolean] =
    existsL(e => !p(e)).map(r => !r)

  /** Creates a new [[monix.eval.Task Task]] that upon execution
    * will signal the first generated element of the source observable.
    *
    * In case the stream was empty, then the `Task` gets completed
    * in error with a `NoSuchElementException`.
    */
  def firstL: Task[A] =
    firstOrElseL(throw new NoSuchElementException("firstL on empty observable"))

  /** Creates a new [[monix.eval.Task Task]] that upon execution
    * will signal the first generated element of the source observable.
    *
    * Returns an `Option` because the source can be empty.
    */
  def firstOptionL: Task[Option[A]] =
    map(Some.apply).firstOrElseL(None)

  /** Creates a new [[monix.eval.Task Task]] that upon execution
    * will signal the first generated element of the source observable.
    *
    * In case the stream was empty, then the given default
    * gets evaluated and emitted.
    */
  def firstOrElseL[B >: A](default: => B): Task[B] =
    Task.create { (s, cb) =>
      unsafeSubscribeFn(new Subscriber.Sync[A] {
        implicit val scheduler: Scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Stop = {
          cb.onSuccess(elem)
          isDone = true
          Stop
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb(Eager(default))
          }
      })
    }

  /** Alias for [[firstOptionL]]. */
  def headOptionL: Task[Option[A]] = firstOptionL
  /** Alias for [[firstL]]. */
  def headL: Task[A] = firstL
  /** Alias for [[firstOrElseL]]. */
  def headOrElseL[B >: A](default: => B): Task[B] = firstOrElseL(default)

  /** Creates a new [[monix.eval.Task Task]] that upon execution
    * will signal the last generated element of the source observable.
    *
    * In case the stream was empty, then the given default gets
    * evaluated and emitted.
    */
  def lastOrElseL[B >: A](default: => B): Task[B] =
    Task.create { (s, cb) =>
      unsafeSubscribeFn(new Subscriber.Sync[A] {
        implicit val scheduler: Scheduler = s
        private[this] var value: A = _
        private[this] var isEmpty = true

        def onNext(elem: A): Continue = {
          if (isEmpty) isEmpty = false
          value = elem
          Continue
        }

        def onError(ex: Throwable): Unit = {
          cb.onError(ex)
        }

        def onComplete(): Unit = {
          if (isEmpty)
            cb(Eager(default))
          else
            cb.onSuccess(value)
        }
      })
    }

  /** Returns a [[monix.eval.Task Task]] that upon execution
    * will signal the last generated element of the source observable.
    *
    * Returns an `Option` because the source can be empty.
    */
  def lastOptionL: Task[Option[A]] =
    map(Some.apply).lastOrElseL(None)

  /** Returns a [[monix.eval.Task Task]] that upon execution
    * will signal the last generated element of the source observable.
    *
    * In case the stream was empty, then the `Task` gets completed
    * in error with a `NoSuchElementException`.
    */
  def lastL: Task[A] =
    lastOrElseL(throw new NoSuchElementException("lastL"))

  /** Returns a task that emits `true` if the source observable is
    * empty, otherwise `false`.
    */
  def isEmptyL: Task[Boolean] =
    isEmptyF.headL

  /** Creates a new [[monix.eval.Task Task]] that will consume the
    * source observable and upon completion of the source it will
    * complete with `Unit`.
    */
  def completedL: Task[Unit] =
    Task.create { (s, cb) =>
      unsafeSubscribeFn(new Subscriber.Sync[A] {
        implicit val scheduler: Scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Continue = Continue
        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; cb.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; cb.onSuccess(()) }
      })
    }

  /** Given a `cats.Order` over the stream's elements, returns the
    * maximum element in the stream.
    *
    * Example:
    * {{{
    *   // Yields Some(20)
    *   Observable(10, 7, 6, 8, 20, 3, 5).maxL
    *
    *   // Yields Observable.empty
    *   Observable.empty.maxL
    * }}}
    *
    * $catsOrderDesc
    *
    * @see [[Observable.maxF maxF]] for the version that returns an
    *      observable instead of a `Task`.
    *
    * @param A is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the maximum element of the source stream, relative
    *         to the defined `Order`
    */
  def maxL[AA >: A](implicit A: Order[AA]): Task[Option[AA]] =
    maxF(A).headOptionL

  /** Takes the elements of the source observable and emits the
    * element that has the maximum key value, where the key is
    * generated by the given function.
    *
    * Example:
    * {{{
    *   case class Person(name: String, age: Int)
    *
    *   // Yields Some(Person("Alex", 34))
    *   Observable(Person("Alex", 34), Person("Alice", 27))
    *     .maxByL(_.age)
    * }}}
    *
    * $catsOrderDesc
    *
    * @see [[Observable.maxByF maxByF]] for the version that returns an
    *      observable instead of a `Task`.
    *
    * @param key is the function that returns the key for which the
    *        given ordering is defined
    *
    * @param K is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the maximum element of the source stream, relative
    *         to its key generated by the given function and the
    *         given ordering
    */
  def maxByL[K](key: A => K)(implicit K: Order[K]): Task[Option[A]] =
    maxByF(key)(K).headOptionL

  /** Given a `cats.Order` over the stream's elements, returns the
    * minimum element in the stream.
    *
    * Example:
    * {{{
    *   // Yields Some(3)
    *   Observable(10, 7, 6, 8, 20, 3, 5).minL
    *
    *   // Yields None
    *   Observable.empty.minL
    * }}}
    *
    * $catsOrderDesc
    *
    * @see [[Observable.minF minF]] for the version that returns an
    *      observable instead of a `Task`.
    *
    * @param A is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the minimum element of the source stream, relative
    *         to the defined `Order`
    */
  def minL[AA >: A](implicit A: Order[AA]): Task[Option[AA]] =
    minF(A).headOptionL

  /** Takes the elements of the source observable and emits the
    * element that has the minimum key value, where the key is
    * generated by the given function.
    *
    * Example:
    * {{{
    *   case class Person(name: String, age: Int)
    *
    *   // Yields Some(Person("Alice", 27))
    *   Observable(Person("Alex", 34), Person("Alice", 27))
    *     .minByL(_.age)
    * }}}
    *
    * $catsOrderDesc
    *
    * @param key is the function that returns the key for which the
    *        given ordering is defined
    *
    * @param K is the `cats.Order` type class instance that's going
    *          to be used for comparing elements
    *
    * @return the minimum element of the source stream, relative
    *         to its key generated by the given function and the
    *         given ordering
    */
  def minByL[K](key: A => K)(implicit K: Order[K]): Task[Option[A]] =
    minByF(key)(K).headOptionL

  /** Returns a task that emits `false` if the source observable is
    * empty, otherwise `true`.
    */
  def nonEmptyL: Task[Boolean] =
    nonEmptyF.headL

  /** Given a source that emits numeric values, the `sum` operator sums
    * up all values and returns the result.
    */
  def sumL[B >: A](implicit B: Numeric[B]): Task[B] =
    sumF(B).headL

  /** Returns a `Task` that upon evaluation will collect all items from
    * the source in a Scala `List` and return this list instead.
    *
    * WARNING: for infinite streams the process will eventually blow up
    * with an out of memory error.
    */
  def toListL: Task[List[A]] =
    foldLeftL(mutable.ListBuffer.empty[A])(_ += _).map(_.toList)

  /** Creates a new [[monix.eval.Task Task]] that will consume the
    * source observable, executing the given callback for each element.
    */
  def foreachL(cb: A => Unit): Task[Unit] =
    Task.create { (s, onFinish) =>
      unsafeSubscribeFn(new ForeachSubscriber[A](cb, onFinish, s))
    }

  /** Subscribes to the source `Observable` and foreach element emitted
    * by the source it executes the given callback.
    */
  def foreach(cb: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] = {
    val p = Promise[Unit]()
    val onFinish = Callback.fromPromise(p)
    val c = unsafeSubscribeFn(new ForeachSubscriber[A](cb, onFinish, s))
    CancelableFuture(p.future, c)
  }
}


/** Observable builders.
  *
  * @define multicastDesc Creates an input channel and an output observable
  *         pair for building a [[MulticastStrategy multicast]] data-source.
  *
  *         Useful for building [[MulticastStrategy multicast]] observables
  *         from data-sources that cannot be back-pressured.
  *
  *         Prefer [[Observable.create]] when possible.
  *
  * @define fromIteratorDesc Converts any `Iterator` into an observable.
  *
  *         WARNING: reading from an `Iterator` is a destructive process.
  *         Therefore only a single subscriber is supported, the result being
  *         a single-subscriber observable. If multiple subscribers are attempted,
  *         all subscribers, except for the first one, will be terminated with a
  *         [[monix.execution.exceptions.APIContractViolationException APIContractViolationException]].
  *
  *         Therefore, if you need a factory of data sources, from a cold source
  *         from which you can open how many iterators you want,
  *         you can use [[Observable.defer]] to build such a factory. Or you can share
  *         the resulting observable by converting it into a
  *         [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
  *         by means of [[Observable!.multicast multicast]].
  *
  * @define fromInputStreamDesc Converts a `java.io.InputStream` into an
  *         observable that will emit `Array[Byte]` elements.
  *
  *         WARNING: reading from the input stream is a destructive process.
  *         Therefore only a single subscriber is supported, the result being
  *         a single-subscriber observable. If multiple subscribers are attempted,
  *         all subscribers, except for the first one, will be terminated with a
  *         [[monix.execution.exceptions.APIContractViolationException APIContractViolationException]].
  *
  *         Therefore, if you need a factory of data sources, from a cold source such
  *         as a `java.io.File` from which you can open how many file handles you want,
  *         you can use [[Observable.defer]] to build such a factory. Or you can share
  *         the resulting observable by converting it into a
  *         [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
  *         by means of [[Observable!.multicast multicast]].
  *
  * @define fromCharsReaderDesc Converts a `java.io.Reader` into an observable
  *         that will emit `Array[Char]` elements.
  *
  *         WARNING: reading from a reader is a destructive process.
  *         Therefore only a single subscriber is supported, the result being
  *         a single-subscriber observable. If multiple subscribers are attempted,
  *         all subscribers, except for the first one, will be terminated with a
  *         [[monix.execution.exceptions.APIContractViolationException APIContractViolationException]].
  *
  *         Therefore, if you need a factory of data sources, from a cold source such
  *         as a `java.io.File` from which you can open how many file handles you want,
  *         you can use [[Observable.defer]] to build such a factory. Or you can share
  *         the resulting observable by converting it into a
  *         [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
  *         by means of [[Observable!.multicast multicast]].
  */
object Observable {
  /** Given a sequence of elements, builds an observable from it. */
  def apply[A](elems: A*): Observable[A] =
    Observable.fromIterable(elems)

  /** Creates an observable that doesn't emit anything, but immediately
    * calls `onComplete` instead.
    */
  def empty[A]: Observable[A] =
    builders.EmptyObservable

  /** Returns an `Observable` that on execution emits the given strict value.
    */
  def now[A](elem: A): Observable[A] =
    new builders.NowObservable(elem)

  /** Lifts an element into the `Observable` context.
    *
    * Alias for [[now]].
    */
  def pure[A](elem: A): Observable[A] =
    new builders.NowObservable(elem)

  /** Given a non-strict value, converts it into an Observable
    * that upon subscription, evaluates the expression and
    * emits a single element.
    */
  def eval[A](a: => A): Observable[A] =
    new builders.EvalAlwaysObservable(a)

  /** Alias for [[eval]]. */
  def delay[A](a: => A): Observable[A] = eval(a)

  /** Given a non-strict value, converts it into an Observable
    * that emits a single element and that memoizes the value
    * for subsequent invocations.
    */
  def evalOnce[A](f: => A): Observable[A] =
    new builders.EvalOnceObservable(f)

  /** Transforms a non-strict [[monix.eval.Coeval Coeval]] value
    * into an `Observable` that emits a single element.
    */
  def coeval[A](value: Coeval[A]): Observable[A] =
    value match {
      case Coeval.Now(a) => Observable.now(a)
      case Coeval.Error(e) => Observable.raiseError(e)
      case other => Observable.eval(other.value)
    }

  /** Lifts a non-strict value into an observable that emits a single element,
    * but upon subscription delay its evaluation by the specified timespan
    */
  def evalDelayed[A](delay: FiniteDuration, a: => A): Observable[A] =
    eval(a).delaySubscription(delay)

  /** Creates an Observable that emits an error.
    */
  def raiseError[A](ex: Throwable): Observable[A] =
    new builders.ErrorObservable(ex)

  /** Creates an Observable that doesn't emit anything and that never
    * completes.
    */
  def never[A]: Observable[A] =
    builders.NeverObservable

  /** Mirrors the given source [[Observable]], but upon subscription
    * ensure that evaluation forks into a separate (logical) thread.
    *
    * The [[monix.execution.Scheduler Scheduler]] used will be
    * the one that is injected in `subscribe()`.
    *
    * @param fa is the observable that will get subscribed
    *        asynchronously
    */
  def fork[A](fa: Observable[A]): Observable[A] =
    fa.executeWithFork

  /** Mirrors the given source [[Observable]], but upon subscription ensure
    * that evaluation forks into a separate (logical) thread,
    * managed by the given scheduler, which will also be used for
    * subsequent asynchronous execution, overriding the default.
    *
    * The given [[monix.execution.Scheduler Scheduler]]  will be
    * used for evaluation of the source [[Observable]], effectively
    * overriding the `Scheduler` that's passed in `subscribe()`.
    * Thus you can evaluate an observable on a separate thread-pool,
    * useful for example in case of doing I/O.
    *
    * @param fa is the source observable that will evaluate asynchronously
    *        on the specified scheduler
    * @param scheduler is the scheduler to use for evaluation
    */
  def fork[A](fa: Observable[A], scheduler: Scheduler): Observable[A] =
    fa.executeOn(scheduler)

  /** Keeps calling `f` and concatenating the resulting observables
    * for each `scala.util.Left` event emitted by the source, concatenating
    * the resulting observables and pushing every `scala.util.Right[B]`
    * events downstream.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    *
    * It helps to wrap your head around it if you think of it as being
    * equivalent to this inefficient and unsafe implementation (for `Observable`):
    *
    * {{{
    *   def tailRecM[A, B](a: A)(f: (A) => Observable[Either[A, B]]): Observable[B] =
    *     f(a).flatMap {
    *       case Right(b) => pure(b)
    *       case Left(nextA) => tailRecM(nextA)(f)
          }
    * }}}
    */
  def tailRecM[A, B](a: A)(f: (A) => Observable[Either[A, B]]): Observable[B] =
    new builders.TailRecMObservable[A,B](a, f)

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
    * concurrent and safe
    * [[monix.reactive.observers.Subscriber.Sync Subscriber.Sync]].
    *
    * This builder represents the safe way of building observables
    * from data-sources that cannot be back-pressured.
    */
  def create[A](overflowStrategy: OverflowStrategy.Synchronous[A])
    (f: Subscriber.Sync[A] => Cancelable): Observable[A] =
    new builders.CreateObservable(overflowStrategy, f)

  /** $multicastDesc
    *
    * @param multicast is the multicast strategy to use (e.g. publish, behavior,
    *        reply, async)
    */
  def multicast[A](multicast: MulticastStrategy[A])
    (implicit s: Scheduler): (Observer.Sync[A], Observable[A]) = {

    val ref = ConcurrentSubject(multicast)
    (ref, ref)
  }

  /** $multicastDesc
    *
    * @param multicast is the multicast strategy to use (e.g. publish, behavior,
    *        reply, async)
    * @param overflow is the overflow strategy for the buffer that gets placed
    *        in front (since this will be a hot data-source that cannot be
    *        back-pressured)
    */
  def multicast[A](multicast: MulticastStrategy[A], overflow: OverflowStrategy.Synchronous[A])
    (implicit s: Scheduler): (Observer.Sync[A], Observable[A]) = {

    val ref = ConcurrentSubject(multicast, overflow)
    (ref, ref)
  }

  /** $fromIteratorDesc
    *
    * @param iterator to transform into an observable
    */
  def fromIterator[A](iterator: Iterator[A]): Observable[A] =
    new builders.IteratorAsObservable[A](iterator, Cancelable.empty)

  /** $fromIteratorDesc
    *
    * This variant of `fromIterator` takes an `onFinish` callback that
    * will be called when the streaming is finished, either with
    * `onComplete`, `onError`, when the downstream signals a `Stop` or
    * when the subscription gets canceled.
    *
    * This `onFinish` callback is guaranteed to be called only once.
    *
    * Useful for controlling resource deallocation (e.g. closing file
    * handles).
    *
    * @param iterator to transform into an observable
    * @param onFinish a callback that will be called for resource deallocation
    *        whenever the iterator is complete, or when the stream is
    *        canceled
    */
  def fromIterator[A](iterator: Iterator[A], onFinish: () => Unit): Observable[A] =
    new builders.IteratorAsObservable[A](iterator, Cancelable(onFinish))

  /** Converts any `Iterable` into an [[Observable]]. */
  def fromIterable[A](iterable: Iterable[A]): Observable[A] =
    new builders.IterableAsObservable[A](iterable)

  /** $fromInputStreamDesc
    *
    * @param in is the `InputStream` to convert into an observable
    */
  def fromInputStream(in: InputStream): Observable[Array[Byte]] =
    fromInputStream(in, chunkSize = 4096)

  /** $fromInputStreamDesc
    *
    * @param in is the `InputStream` to convert into an observable
    * @param chunkSize is the maximum length of the emitted arrays of bytes.
    *        It's also used when reading from the input stream.
    */
  def fromInputStream(in: InputStream, chunkSize: Int): Observable[Array[Byte]] =
    new builders.InputStreamObservable(in, chunkSize)

  /** $fromCharsReaderDesc
    *
    * @param in is the `Reader` to convert into an observable
    */
  def fromCharsReader(in: Reader): Observable[Array[Char]] =
    fromCharsReader(in, chunkSize = 4096)

  /** $fromCharsReaderDesc
    *
    * @param in is the `Reader` to convert into an observable
    * @param chunkSize is the maximum length of the emitted arrays of chars.
    *        It's also used when reading from the reader.
    */
  def fromCharsReader(in: Reader, chunkSize: Int): Observable[Array[Char]] =
    new builders.CharsReaderObservable(in, chunkSize)

  /** Converts a `java.io.BufferedReader` into an
    * observable that will emit `String` text lines from the input.
    *
    * Note that according to the specification of `BufferedReader`, a
    * line is considered to be terminated by any one of a line
    * feed (`\n`), a carriage return (`\r`), or a carriage return
    * followed immediately by a linefeed.
    *
    * WARNING: reading from a reader is a destructive process.
    * Therefore only a single subscriber is supported, the result being
    * a single-subscriber observable. If multiple subscribers are attempted,
    * all subscribers, except for the first one, will be terminated with a
    * [[monix.execution.exceptions.APIContractViolationException APIContractViolationException]].
    *
    * Therefore, if you need a factory of data sources, from a cold source such
    * as a `java.io.File` from which you can open how many file handles you want,
    * you can use [[Observable.defer]] to build such a factory. Or you can share
    * the resulting observable by converting it into a
    * [[monix.reactive.observables.ConnectableObservable ConnectableObservable]]
    * by means of [[Observable!.multicast multicast]].
    *
    * @param in is the `Reader` to convert into an observable
    */
  def fromLinesReader(in: BufferedReader): Observable[String] =
    new builders.LinesReaderObservable(in)

  /** Given a `org.reactivestreams.Publisher`, converts it into a
    * Monix / Rx Observable.
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    *
    * @see [[Observable.toReactive]] for converting an `Observable` to
    *      a reactive publisher.
    *
    * @param publisher is the `org.reactivestreams.Publisher` reference to
    *        wrap into an [[Observable]]
    */
  def fromReactivePublisher[A](publisher: RPublisher[A]): Observable[A] =
    new builders.ReactiveObservable[A](publisher, 0)

  /** Given a `org.reactivestreams.Publisher`, converts it into a
    * Monix / Rx Observable.
    *
    * See the [[http://www.reactive-streams.org/ Reactive Streams]]
    * protocol that Monix implements.
    *
    * @see [[Observable.toReactive]] for converting an `Observable` to
    *      a reactive publisher.
    *
    * @param publisher is the `org.reactivestreams.Publisher` reference to
    *        wrap into an [[Observable]]
    *
    * @param requestCount a strictly positive number, representing the size
    *        of the buffer used and the number of elements requested on each
    *        cycle when communicating demand, compliant with the
    *        reactive streams specification. If `Int.MaxValue` is given,
    *        then no back-pressuring logic will be applied (e.g. an unbounded
    *        buffer is used and the source has a license to stream as many
    *        events as it wants).
    */
  def fromReactivePublisher[A](publisher: RPublisher[A], requestCount: Int): Observable[A] =
    new builders.ReactiveObservable[A](publisher, requestCount)

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
  def defer[A](fa: => Observable[A]): Observable[A] =
    new builders.DeferObservable(fa _)

  /** Alias for [[defer]]. */
  def suspend[A](fa: => Observable[A]): Observable[A] = defer(fa)

  /** Builds a new observable from a strict `head` and a lazily
    * evaluated tail.
    */
  def cons[A](head: A, tail: Observable[A]): Observable[A] =
    new builders.ConsObservable[A](head, tail)

  /** Creates a new observable from this observable and another given
    * observable by interleaving their items into a strictly alternating sequence.
    *
    * So the first item emitted by the new observable will be the item emitted by
    * `self`, the second item will be emitted by the other observable, and so forth;
    * when either `self` or `other` calls `onCompletes`, the items will then be
    * directly coming from the observable that has not completed; when `onError` is
    * called by either `self` or `other`, the new observable will call `onError` and halt.
    *
    * See [[merge]] for a more relaxed alternative that doesn't
    * emit items in strict alternating sequence.
    */
  def interleave2[A](oa1: Observable[A], oa2: Observable[A]): Observable[A] =
    new builders.Interleave2Observable(oa1, oa2)

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
  def fromStateAction[S, A](f: S => (A, S))(seed: => S): Observable[A] =
    new builders.StateActionObservable(seed, f)

  /** Given an initial state and a generator function that produces the
    * next state and the next element in the sequence, creates an
    * observable that keeps generating elements produced by our
    * generator function.
    */
  def fromAsyncStateAction[S, A](f: S => Task[(A, S)])(seed: => S): Observable[A] =
    new builders.AsyncStateActionObservable(seed, f)

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
    Observable.fromIterable(sources).concatDelayErrors

  /** Merges the given list of ''observables'' into a single observable.
    */
  def merge[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.fromIterable(sources).mergeMap(identity)(os)

  /** Merges the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def mergeDelayError[A](sources: Observable[A]*)
    (implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] =
    Observable.fromIterable(sources).mergeMapDelayErrors(identity)(os)

  /** Concatenates the given list of ''observables'' into a single
    * observable.
    */
  def concat[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concatMap[A](identity)

  /** Concatenates the given list of ''observables'' into a single observable.
    * Delays errors until the end.
    */
  def concatDelayError[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).concatMapDelayErrors[A](identity)

  /**
    * like `zipWith2`, but when `oa1` completes before `oa2` does, the new observable will
    * continue emitting items as `f(paddingA1, oa2.onNext())` until `oa2` completes;
    * and vice versa, when `oa2` completes before `oa1` does, the new observable will
    * continue emitting items as `f(oa1.onNext(), paddingA2)` until `oa1` completes.
    */
  def padWith2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2], paddingA1: Coeval[A1], paddingA2: Coeval[A2])(f: (A1,A2) ⇒ R): Observable[R] =
    new builders.Pad2Observable(oa1, oa2, paddingA1, paddingA2)(f)

  /**
    * like `zip2`, but when `oa1` completes before `oa2` does, the new observable will
    * continue emitting pairs as (`paddingA1`, `oa2`.onNext()) until `oa2` completes;
    * and vice versa, when `oa2` completes before `oa1` does, the new observable will
    * continue emitting pairs as (`oa1`.onNext(), `paddingA2`) until `oa1` completes.
    */
  def pad2[A1, A2](oa1: Observable[A1], oa2: Observable[A2], paddingA1: Coeval[A1], paddingA2: Coeval[A2]): Observable[(A1,A2)] =
    padWith2(oa1, oa2, paddingA1, paddingA2)((a1,a2) ⇒ (a1,a2))

  /** Given a sequence of observables, builds an observable
    * that emits the elements of the most recently emitted
    * observable.
    */
  def switch[A](sources: Observable[A]*): Observable[A] =
    Observable.fromIterable(sources).switch

  /** Creates a new observable from two observable sequences
    * by combining their items in pairs in a strict sequence.
    *
    * So the first item emitted by the new observable will be the result
    * of the function applied to the first items emitted by each of
    * the source observables; the second item emitted by the new observable
    * will be the result of the function applied to the second items
    * emitted by each of those observables; and so forth.
    *
    * See [[combineLatestMap2]] for a more relaxed alternative that doesn't
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
    * See [[combineLatestMap2]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipMap2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2])(f: (A1,A2) => R): Observable[R] =
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
    * See [[combineLatestMap3]] for a more relaxed alternative that doesn't
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
    * See [[combineLatestMap3]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipMap3[A1,A2,A3,R](oa1: Observable[A1], oa2: Observable[A2], oa3: Observable[A3])
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
    * See [[combineLatestMap4]] for a more relaxed alternative that doesn't
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
    * See [[combineLatestMap4]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipMap4[A1,A2,A3,A4,R]
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
    * See [[combineLatestMap5]] for a more relaxed alternative that doesn't
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
    * See [[combineLatestMap5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipMap5[A1,A2,A3,A4,A5,R]
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
    * See [[combineLatestMap5]] for a more relaxed alternative that doesn't
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
    * See [[combineLatestMap5]] for a more relaxed alternative that doesn't
    * combine items in strict sequence.
    *
    * @param f is the mapping function applied over the generated pairs
    */
  def zipMap6[A1,A2,A3,A4,A5,A6,R]
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
        acc.zipMap(obs)((seq, elem) => seq :+ elem)
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
    * This operator behaves in a similar way to [[zipMap2]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestMap2[A1,A2,R](oa1: Observable[A1], oa2: Observable[A2])
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
    * This operator behaves in a similar way to [[zipMap3]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestMap3[A1,A2,A3,R](a1: Observable[A1], a2: Observable[A2], a3: Observable[A3])
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
    * This operator behaves in a similar way to [[zipMap4]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestMap4[A1,A2,A3,A4,R]
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
    * This operator behaves in a similar way to [[zipMap5]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestMap5[A1,A2,A3,A4,A5,R]
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
    * This operator behaves in a similar way to [[zipMap6]],
    * but while `zip` emits items only when all of the zipped source
    * observables have emitted a previously unzipped item, `combine`
    * emits an item whenever any of the source Observables emits an
    * item (so long as each of the source Observables has emitted at
    * least one item).
    */
  def combineLatestMap6[A1,A2,A3,A4,A5,A6,R]
    (a1: Observable[A1], a2: Observable[A2], a3: Observable[A3],
     a4: Observable[A4], a5: Observable[A5], a6: Observable[A6])
    (f: (A1,A2,A3,A4,A5,A6) => R): Observable[R] =
    new builders.CombineLatest6Observable[A1,A2,A3,A4,A5,A6,R](a1,a2,a3,a4,a5,a6)(f)

  /** Given an observable sequence, it combines them together
    * (using [[combineLatestMap2 combineLatest]])
    * returning a new observable that generates sequences.
    */
  def combineLatestList[A](sources: Observable[A]*): Observable[Seq[A]] = {
    if (sources.isEmpty) Observable.empty
    else {
      val seed = sources.head.map(t => Vector(t))
      sources.tail.foldLeft(seed) { (acc, obs) =>
        acc.combineLatestMap(obs) { (seq, elem) => seq :+ elem }
      }
    }
  }

  /** Given a list of source Observables, emits all of the items from
    * the first of these Observables to emit an item or to complete,
    * and cancel the rest.
    */
  def firstStartedOf[A](source: Observable[A]*): Observable[A] =
    new builders.FirstStartedObservable(source: _*)

  /** Implicit type class instances for [[Observable]]. */
  implicit val catsInstances: CatsInstances =
    new CatsInstances

  /** Cats instances for [[Observable]]. */
  class CatsInstances extends MonadError[Observable, Throwable]
    with MonoidK[Observable]
    with CoflatMap[Observable] {

    override def pure[A](a: A): Observable[A] = Observable.now(a)
    override val unit: Observable[Unit] = Observable.now(())

    override def combineK[A](x: Observable[A], y: Observable[A]): Observable[A] =
      x ++ y
    override def flatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Observable[Observable[A]]): Observable[A] =
      ffa.flatten
    override def tailRecM[A, B](a: A)(f: (A) => Observable[Either[A, B]]): Observable[B] =
      Observable.tailRecM(a)(f)
    override def coflatMap[A, B](fa: Observable[A])(f: (Observable[A]) => B): Observable[B] =
      Observable.eval(f(fa))
    override def ap[A, B](ff: Observable[(A) => B])(fa: Observable[A]): Observable[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Observable[A], fb: Observable[B])(f: (A, B) => Z): Observable[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Observable[A] =
      Observable.raiseError(e)
    override def handleError[A](fa: Observable[A])(f: (Throwable) => A): Observable[A] =
      fa.onErrorHandle(f)
    override def handleErrorWith[A](fa: Observable[A])(f: (Throwable) => Observable[A]): Observable[A] =
      fa.onErrorHandleWith(f)
    override def recover[A](fa: Observable[A])(pf: PartialFunction[Throwable, A]): Observable[A] =
      fa.onErrorRecover(pf)
    override def recoverWith[A](fa: Observable[A])(pf: PartialFunction[Throwable, Observable[A]]): Observable[A] =
      fa.onErrorRecoverWith(pf)
    override def empty[A]: Observable[A] =
      Observable.empty[A]
  }
}
