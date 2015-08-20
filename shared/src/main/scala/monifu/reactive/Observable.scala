/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive

import java.io.PrintStream
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.{default => defaultPolicy}
import monifu.reactive.observers._
import monifu.reactive.subjects.{AsyncSubject, BehaviorSubject, PublishSubject, ReplaySubject}
import org.reactivestreams.{Publisher, Subscriber => RSubscriber}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.util.control.NonFatal


/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] { self =>
  /**
   * Characteristic function for an `Observable` instance,
   * that creates the subscription and that eventually starts the streaming of events
   * to the given [[Observer]], being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   */
  protected def subscribeFn(subscriber: Subscriber[T]): Unit

  /**
   * Creates the subscription and that starts the stream.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  def subscribe(observer: Observer[T])(implicit s: Scheduler): BooleanCancelable = {
    val cancelable = BooleanCancelable()
    takeWhileNotCanceled(cancelable).unsafeSubscribe(SafeObserver[T](observer))
    cancelable
  }

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit, completedFn: () => Unit)
      (implicit s: Scheduler): BooleanCancelable = {

    subscribe(new Observer[T] {
      def onNext(elem: T) = nextFn(elem)
      def onComplete() = completedFn()
      def onError(ex: Throwable) = errorFn(ex)
    })
  }

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit)(implicit s: Scheduler): BooleanCancelable =
    subscribe(nextFn, errorFn, () => ())

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe()(implicit s: Scheduler): Cancelable =
    subscribe(elem => Continue)

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack])(implicit s: Scheduler): BooleanCancelable =
    subscribe(nextFn, error => s.reportFailure(error), () => ())

  /**
   * Creates the subscription that eventually starts the stream.
   *
   * This function is "unsafe" to call because it does not protect the calls to the
   * given [[Observer]] implementation in regards to unexpected exceptions that
   * violate the contract, therefore the given instance must respect its contract
   * and not throw any exceptions when the observable calls `onNext`,
   * `onComplete` and `onError`. if it does, then the behavior is undefined.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] that respects
   *                 Monifu Rx contract.
   */
  def unsafeSubscribe(observer: Observer[T])(implicit s: Scheduler): Unit = {
    subscribeFn(Subscriber(observer, s))
  }

  /**
   * Creates the subscription that eventually starts the stream.
   *
   * This function is "unsafe" to call because it does not protect the calls to the
   * given [[Observer]] implementation in regards to unexpected exceptions that
   * violate the contract, therefore the given instance must respect its contract
   * and not throw any exceptions when the observable calls `onNext`,
   * `onComplete` and `onError`. if it does, then the behavior is undefined.
   */
  def unsafeSubscribe(subscriber: Subscriber[T]): Unit = {
    subscribeFn(subscriber)
  }

  /**
   * Wraps this Observable into a `org.reactivestreams.Publisher`.
   */
  def publisher[U >: T](implicit s: Scheduler): Publisher[U] =
    new Publisher[U] {
      def subscribe(subscriber: RSubscriber[_ >: U]): Unit = {
        subscribeFn(Subscriber(SafeObserver(Observer.from(subscriber)), s))
      }
    }

  /**
   * Returns an Observable that applies the given function to each item emitted by an
   * Observable and emits the result.
   *
   * @param f a function to apply to each item emitted by the Observable
   * @return an Observable that emits the items from the source Observable, transformed by the given function
   */
  def map[U](f: T => U): Observable[U] =
    operators.map(self)(f)

  /**
   * Returns an Observable which only emits those items for which the given predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable,
   *          returning `true` if they pass the filter
   *
   * @return an Observable that emits only those items in the original Observable
   *         for which the filter evaluates as `true`
   */
  def filter(p: T => Boolean): Observable[T] =
    operators.filter(self)(p)

  /**
   * Returns an Observable by applying the given partial function to the source observable
   * for each element for which the given partial function is defined.
   *
   * Useful to be used instead of a filter & map combination.
   *
   * @param pf the function that filters and maps the resulting observable
   * @return an Observable that emits the transformed items by the given partial function
   */
  def collect[U](pf: PartialFunction[T, U]): Observable[U] =
    operators.collect(self)(pf)

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then concatenating those
   * resulting Observables and emitting the results of this concatenation.
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and concatenating the results of the Observables
   *         obtained from this transformation.
   */
  def flatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).flatten

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then concatenating those
   * resulting Observables and emitting the results of this concatenation.
   *
   * It's an alias for [[Observable.concatMapDelayError]].
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and concatenating the results of the Observables
   *         obtained from this transformation.
   */
  def flatMapDelayError[U](f: T => Observable[U]): Observable[U] =
    map(f).concatDelayError

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then concatenating those
   * resulting Observables and emitting the results of this concatenation.
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and concatenating the results of the Observables
   *         obtained from this transformation.
   */
  def concatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).concat

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then concatenating those
   * resulting Observables and emitting the results of this concatenation.
   *
   * It's like [[Observable.concatMap]], except that the created observable is reserving onError
   * notifications until all of the merged Observables complete and only then passing it along
   * to the observers.
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and concatenating the results of the Observables
   *         obtained from this transformation.
   */
  def concatMapDelayError[U](f: T => Observable[U]): Observable[U] =
    map(f).concatDelayError

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then merging those
   * resulting Observables and emitting the results of this merger.
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and merging the results of the Observables
   *         obtained from this transformation.
   */
  def mergeMap[U](f: T => Observable[U]): Observable[U] =
    map(f).merge()

  /**
   * Creates a new Observable by applying a function that you supply to each item emitted by
   * the source Observable, where that function returns an Observable, and then merging those
   * resulting Observables and emitting the results of this merger.
   *
   * It's like [[Observable.mergeMap]], except that the created observable is reserving onError
   * notifications until all of the merged Observables complete and only then passing it along
   * to the observers.
   *
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and merging the results of the Observables
   *         obtained from this transformation.
   */
  def mergeMapDelayError[U](f: T => Observable[U]): Observable[U] =
    map(f).mergeDelayError()

  /**
   * Flattens the sequence of Observables emitted by the source into one Observable, without any
   * transformation.
   *
   * It's an alias for [[Observable.concat]].
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] =
    concat

  /**
   * Flattens the sequence of Observables emitted by the source into one Observable, without any
   * transformation. Delays errors until the end.
   *
   * It's an alias for [[Observable.concatDelayError]].
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def flattenDelayError[U](implicit ev: T <:< Observable[U]): Observable[U] =
    concatDelayError

  /**
   * Concatenates the sequence of Observables emitted by the source into one Observable, without any
   * transformation.
   *
   * You can combine the items emitted by multiple Observables so that they act like a single
   * Observable by using this method.
   *
   * The difference between [[concat]] and [[Observable!.merge merge]] is
   * that `concat` cares about ordering of emitted items (e.g. all items emitted by the first observable
   * in the sequence will come before the elements emitted by the second observable), whereas `merge`
   * doesn't care about that (elements get emitted as they come). Because of back-pressure applied to observables,
   * [[concat]] is safe to use in all contexts, whereas [[merge]] requires buffering.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def concat[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.concat(self, delayErrors = false)

  /**
   * Concatenates the sequence of Observables emitted by the source into one Observable, without any
   * transformation.
   *
   * It's like [[Observable.concat]], except that the created observable is reserving onError
   * notifications until all of the merged Observables complete and only then passing it along
   * to the observers.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def concatDelayError[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.concat(self, delayErrors = true)

  /**
   * Merges the sequence of Observables emitted by the source into one Observable, without any
   * transformation.
   *
   * You can combine the items emitted by multiple Observables so that they act like a single
   * Observable by using this method.
   *
   * The difference between [[concat]] and [[merge]] is that `concat` cares about ordering of
   * emitted items (e.g. all items emitted by the first observable in the sequence will come before
   * the elements emitted by the second observable), whereas `merge` doesn't care about that
   * (elements get emitted as they come). Because of back-pressure applied to observables,
   * [[concat]] is safe to use in all contexts, whereas [[merge]] requires buffering.
   *
   * @param bufferPolicy the policy used for buffering, useful if you want to limit the buffer size and
   *                     apply back-pressure, trigger and error, etc... see the
   *                     available [[monifu.reactive.BufferPolicy buffer policies]].
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](bufferPolicy: BufferPolicy[U] = defaultPolicy)
      (implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.merge(self, bufferPolicy, delayErrors = false)

  /**
   * Merges the sequence of Observables emitted by the source into one Observable, without any
   * transformation. You can combine the items emitted by multiple Observables so that they act
   * like a single Observable by using this method.
   *
   * It's like [[Observable.merge]], except that the created observable is reserving onError
   * notifications until all of the merged Observables complete and only then passing it along
   * to the observers.
   *
   * @param bufferPolicy the policy used for buffering, useful if you want to limit the buffer size and
   *                     apply back-pressure, trigger and error, etc... see the
   *                     available [[monifu.reactive.BufferPolicy buffer policies]].
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def mergeDelayError[U](bufferPolicy: BufferPolicy[U] = defaultPolicy)
      (implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.merge(self, bufferPolicy, delayErrors = true)

  /**
   * Convert an Observable that emits Observables into a single Observable that
   * emits the items emitted by the most-recently-emitted of those Observables.
   */
  def switch[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.switch(self, delayErrors = false)

  /**
   * Convert an Observable that emits Observables into a single Observable that
   * emits the items emitted by the most-recently-emitted of those Observables.
   *
   * It's like [[Observable.switch]], except that the created observable is
   * reserving onError notifications until all of the Observables complete
   * and only then passing the error along to the observers.
   */
  def switchDelayError[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.switch(self, delayErrors = true)

  /**
   * Given the source observable and another `Observable`, emits all of the items
   * from the first of these Observables to emit an item and cancel the other.
   */
  def ambWith[U >: T](other: Observable[U]): Observable[U] = {
    Observable.amb(self, other)
  }

  /**
   * Emit items from the source Observable, or emit a default item if
   * the source Observable completes after emitting no items.
   */
  def defaultIfEmpty[U >: T](default: U): Observable[U] =
    operators.misc.defaultIfEmpty(self, default)

  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  def take(n: Int): Observable[T] =
    operators.take.left(self, n)

  /**
   * Creates a new Observable that emits the events of the source, only
   * for the specified `timestamp`, after which it completes.
   *
   * @param timespan the window of time during which the new Observable
   *                 is allowed to emit the events of the source
   */
  def take(timespan: FiniteDuration): Observable[T] =
    operators.take.leftByTimespan(self, timespan)

  /**
   * Creates a new Observable that only emits the last `n` elements
   * emitted by the source.
   */
  def takeRight(n: Int): Observable[T] =
    operators.take.right(self, n)

  /**
   * Drops the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to drop
   *  @return    a new Observable that drops the first ''n'' elements
   *             emitted by the source
   */
  def drop(n: Int): Observable[T] =
    operators.drop.byCount(self, n)

  /**
   * Creates a new Observable that drops the events of the source, only
   * for the specified `timestamp` window.
   *
   * @param timespan the window of time during which the new Observable
   *                 is must drop the events emitted by the source
   */
  def dropByTimespan(timespan: FiniteDuration): Observable[T] =
    operators.drop.byTimespan(self, timespan)

  /**
   * Drops the longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits the rest.
   */
  def dropWhile(p: T => Boolean): Observable[T] =
    operators.drop.byPredicate(self)(p)

  /**
   * Drops the longest prefix of elements that satisfy the given function
   * and returns a new Observable that emits the rest. In comparison with
   * [[dropWhile]], this version accepts a function that takes an additional
   * parameter: the zero-based index of the element.
   */
  def dropWhileWithIndex(p: (T, Int) => Boolean): Observable[T] =
    operators.drop.byPredicateWithIndex(self)(p)


  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(p: T => Boolean): Observable[T] =
    operators.take.byPredicate(self)(p)

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhileNotCanceled(c: BooleanCancelable): Observable[T] =
    operators.take.takeWhileNotCanceled(self, c)

  /**
   * Creates a new Observable that emits the total number of `onNext` events
   * that were emitted by the source.
   *
   * Note that this Observable emits only one item after the source is complete.
   * And in case the source emits an error, then only that error will be
   * emitted.
   */
  def count(): Observable[Long] =
    operators.math.count(self)

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time. This version
   * of `buffer` is emitting items once the internal buffer has the reached the
   * given count.
   *
   * So in this example, we are creating a new observable that emits sequences of
   * exactly 10 elements (or whatever is in the buffer when `onComplete` happens):
   * {{{
   *   observable.buffer(10)
   * }}}
   *
   * @param count the bundle size
   */
  def buffer(count: Int): Observable[Seq[T]] =
    operators.buffer.sized(self, count)

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time.
   *
   * This version of `buffer` emits a new bundle of items periodically,
   * every timespan amount of time, containing all items emitted by the
   * source Observable since the previous bundle emission.
   *
   * @param timespan the interval of time at which it should emit the buffered bundle
   */
  def bufferTimed(timespan: FiniteDuration): Observable[Seq[T]] =
    operators.buffer.timed(self, timespan)

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time.
   *
   * This version of `buffer` emits a new bundle of items periodically,
   * every timespan amount of time, containing all items emitted by the
   * source Observable since the previous bundle emission, or when the buffer
   * size has reached the given `count`.
   *
   * @param maxSize is the maximum bundle size
   * @param timespan the interval of time at which it should emit the buffered bundle
   */
  def bufferSizedAndTimed(maxSize: Int, timespan: FiniteDuration): Observable[Seq[T]] =
    operators.buffer.sizedAndTimed(self, maxSize, timespan)

  /**
   * Emit the most recent items emitted by an Observable within periodic time
   * intervals.
   *
   * Use the sample() method to periodically look at an Observable
   * to see what item it has most recently emitted since the previous
   * sampling. Note that if the source Observable has emitted no
   * items since the last time it was sampled, the Observable that
   * results from the sample( ) operator will emit no item for
   * that sampling period.
   *
   * @param delay the timespan at which sampling occurs and note that this is
   *              not accurate as it is subject to back-pressure concerns - as in
   *              if the delay is 1 second and the processing of an event on `onNext`
   *              in the observer takes one second, then the actual sampling delay
   *              will be 2 seconds.
   */
  def sample(delay: FiniteDuration): Observable[T] =
    sample(delay, delay)

  /**
   * Emit the most recent items emitted by an Observable within periodic time
   * intervals.
   *
   * Use the sample() method to periodically look at an Observable
   * to see what item it has most recently emitted since the previous
   * sampling. Note that if the source Observable has emitted no
   * items since the last time it was sampled, the Observable that
   * results from the sample( ) operator will emit no item for
   * that sampling period.
   *
   * @param initialDelay the initial delay after which sampling can happen
   *
   * @param delay the timespan at which sampling occurs and note that this is
   *              not accurate as it is subject to back-pressure concerns - as in
   *              if the delay is 1 second and the processing of an event on `onNext`
   *              in the observer takes one second, then the actual sampling delay
   *              will be 2 seconds.
   */
  def sample(initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    operators.sample.once(self, initialDelay, delay)

  /**
   * Emit the most recent items emitted by an Observable within periodic time
   * intervals. If no new value has been emitted since the last time it
   * was sampled, the emit the last emitted value anyway.
   *
   * Also see [[Observable.sample]].
   *
   * @param delay the timespan at which sampling occurs and note that this is
   *              not accurate as it is subject to back-pressure concerns - as in
   *              if the delay is 1 second and the processing of an event on `onNext`
   *              in the observer takes one second, then the actual sampling delay
   *              will be 2 seconds.
   */
  def sampleRepeated(delay: FiniteDuration): Observable[T] =
    sampleRepeated(delay, delay)

  /**
   * Emit the most recent items emitted by an Observable within periodic time
   * intervals. If no new value has been emitted since the last time it
   * was sampled, the emit the last emitted value anyway.
   *
   * Also see [[Observable!.sample]].
   *
   * @param initialDelay the initial delay after which sampling can happen
   *
   * @param delay the timespan at which sampling occurs and note that this is
   *              not accurate as it is subject to back-pressure concerns - as in
   *              if the delay is 1 second and the processing of an event on `onNext`
   *              in the observer takes one second, then the actual sampling delay
   *              will be 2 seconds.
   */
  def sampleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    operators.sample.repeated(self, initialDelay, delay)

  /**
   * Only emit an item from an Observable if a particular
   * timespan has passed without it emitting another item.
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then no items will be emitted
   * by the resulting Observable.
   *
   * @param timeout the length of the window of time that must pass after
   *                the emission of an item from the source Observable in which
   *                that Observable emits no items in order for the item to
   *                be emitted by the resulting Observable
   */
  def debounce(timeout: FiniteDuration): Observable[T] =
    operators.debounce.apply(self, timeout)

  /**
   * Hold an Observer's subscription request until the given `future` completes,
   * before passing it on to the source Observable. If the given `future`
   * completes in error, then the subscription is terminated with `onError`.
   *
   * @param future the `Future` that must complete in order for the
   *               subscription to happen.
   */
  def delaySubscription(future: Future[_]): Observable[T] =
    operators.delaySubscription.onFuture(self, future)

  /**
   * Hold an Observer's subscription request for a specified
   * amount of time before passing it on to the source Observable.
   *
   * @param timespan is the time to wait before the subscription
   *                 is being initiated.
   */
  def delaySubscription(timespan: FiniteDuration): Observable[T] =
    operators.delaySubscription.onTimespan(self, timespan)

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R] =
    operators.foldLeft(self, initial)(op)

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def reduce[U >: T](op: (U, U) => U): Observable[U] =
    operators.reduce(self : Observable[U])(op)

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits on each step the result
   * of the applied function.
   *
   * Similar to [[foldLeft]], but emits the state on each step. Useful for modeling finite
   * state machines.
   */
  def scan[R](initial: R)(op: (R, T) => R): Observable[R] =
    operators.scan(self, initial)(op)

  /**
   * Applies a binary operator to a start value and to elements produced
   * by the source observable, going from left to right, producing
   * and concatenating observables along the way.
   *
   * It's the combination between [[monifu.reactive.Observable.scan scan]]
   * and [[monifu.reactive.Observable.flatten]].
   */
  def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Observable[R] =
    operators.flatScan(self, initial)(op)

  /**
   * Applies a binary operator to a start value and to elements produced
   * by the source observable, going from left to right, producing
   * and concatenating observables along the way.
   *
   * It's the combination between [[monifu.reactive.Observable.scan scan]]
   * and [[monifu.reactive.Observable.flattenDelayError]].
   */
  def flatScanDelayError[R](initial: R)(op: (R, T) => Observable[R]): Observable[R] =
    operators.flatScan.delayError(self, initial)(op)

  /**
   * Executes the given callback when the stream has ended,
   * but before the complete event is emitted.
   *
   * @param cb the callback to execute when the subscription is canceled
   */
  def doOnComplete(cb: => Unit): Observable[T] =
    operators.doWork.onComplete(self)(cb)

  /**
   * Executes the given callback for each element generated by the source
   * Observable, useful for doing side-effects.
   *
   * @return a new Observable that executes the specified callback for each element
   */
  def doWork(cb: T => Unit): Observable[T] =
    operators.doWork.onNext(self)(cb)

  /**
   * Executes the given callback only for the first element generated by the source
   * Observable, useful for doing a piece of computation only when the stream started.
   *
   * @return a new Observable that executes the specified callback only for the first element
   */
  def doOnStart(cb: T => Unit): Observable[T] =
    operators.doWork.onStart(self)(cb)

  /**
   * Executes the given callback if the downstream observer
   * has canceled the streaming.
   */
  def doOnCanceled(cb: => Unit): Observable[T] =
    operators.doWork.onCanceled(self)(cb)

  /**
   * Executes the given callback when the stream is interrupted
   * with an error, before the `onError` event is emitted downstream.
   *
   * NOTE: should protect the code in this callback, because if it
   * throws an exception the `onError` event will prefer signaling the
   * original exception and otherwise the behavior is undefined.
   */
  def doOnError(cb: Throwable => Unit): Observable[T] =
    operators.doWork.onError(self)(cb)

  /**
   * Returns an Observable which only emits the first item for which the predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only the first item in the original Observable for which the filter evaluates as `true`
   */
  def find(p: T => Boolean): Observable[T] =
    filter(p).head

  /**
   * Returns an Observable which emits a single value, either true, in case the given predicate holds for at least
   * one item, or false otherwise.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for at least one item
   */
  def exists(p: T => Boolean): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  /**
   * Returns an Observable that emits true if the source Observable
   * is empty, otherwise false.
   */
  def isEmpty: Observable[Boolean] =
    operators.misc.isEmpty(self)

  /**
   * Returns an Observable that emits false if the source Observable
   * is empty, otherwise true.
   */
  def nonEmpty: Observable[Boolean] =
    operators.misc.isEmpty(self).map(isEmpty => !isEmpty)

  /**
   * Returns an Observable that emits a single boolean, either true, in case the given predicate holds for all the items
   * emitted by the source, or false in case at least one item is not verifying the given predicate.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for all the items
   */
  def forAll(p: T => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  /**
   * Returns an Observable that doesn't emit anything,
   * but that completes when the source Observable completes.
   */
  def complete: Observable[Nothing] =
    operators.misc.complete(this)

  /**
   * Returns an Observable that emits a single Throwable,
   * in case an error was thrown by the source Observable,
   * otherwise it isn't going to emit anything.
   */
  def error: Observable[Throwable] =
    operators.misc.error(this)

  /**
   * Emits the given exception instead of `onComplete`.
   * @param error the exception to emit onComplete
   * @return a new Observable that emits an exception onComplete
   */
  def endWithError(error: Throwable): Observable[T] =
    operators.misc.endWithError(this)(error)

  /**
   * Creates a new Observable that emits the given element
   * and then it also emits the events of the source (prepend operation).
   */
  def +:[U >: T](elem: U): Observable[U] =
    Observable.unit(elem) ++ this

  /**
   * Creates a new Observable that emits the given elements
   * and then it also emits the events of the source (prepend operation).
   */
  def startWith[U >: T](elems: U*): Observable[U] =
    Observable.fromIterable(elems) ++ this

  /**
   * Creates a new Observable that emits the events of the source
   * and then it also emits the given element (appended to the stream).
   */
  def :+[U >: T](elem: U): Observable[U] =
    this ++ Observable.unit(elem)

  /**
   * Creates a new Observable that emits the events of the source
   * and then it also emits the given elements (appended to the stream).
   */
  def endWith[U >: T](elems: U*): Observable[U] =
    this ++ Observable.fromIterable(elems)

  /**
   * Concatenates the source Observable with the other Observable, as specified.
   */
  def ++[U >: T](other: => Observable[U]): Observable[U] =
    Observable.concat(this, other)

  /**
   * Only emits the first element emitted by the source observable, after which it's completed immediately.
   */
  def head: Observable[T] = take(1)

  /**
   * Drops the first element of the source observable, emitting the rest.
   */
  def tail: Observable[T] = drop(1)

  /**
   * Only emits the last element emitted by the source observable, after which it's completed immediately.
   */
  def last: Observable[T] =
    takeRight(1)

  /**
   * Emits the first element emitted by the source, or otherwise if the source is completed without
   * emitting anything, then the `default` is emitted.
   */
  def headOrElse[B >: T](default: => B): Observable[B] =
    head.foldLeft(Option.empty[B])((_, elem) => Some(elem)) map {
      case Some(elem) => elem
      case None => default
    }

  /**
   * Emits the first element emitted by the source, or otherwise if the source is completed without
   * emitting anything, then the `default` is emitted.
   *
   * Alias for `headOrElse`.
   */
  def firstOrElse[U >: T](default: => U): Observable[U] =
    headOrElse(default)

  /**
   * Creates a new Observable from this Observable and another given Observable,
   * by emitting elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are ignored.
   */
  def zip[U](other: Observable[U]): Observable[(T, U)] =
    operators.zip(self, other)

  /**
   * Creates a new Observable from this Observable and another given Observable.
   *
   * This operator behaves in a similar way to [[zip]], but while `zip` emits items
   * only when all of the zipped source Observables have emitted a previously unzipped item,
   * `combine` emits an item whenever any of the source Observables emits
   * an item (so long as each of the source Observables has emitted at least one item).
   */
  def combineLatest[U](other: Observable[U]): Observable[(T, U)] =
    operators.combineLatest(self, other, delayErrors = false)

  /**
   * Creates a new Observable from this Observable and another given Observable.
   *
   * It's like [[Observable.combineLatest]], except that the created observable is reserving onError
   * notifications until all of the combined Observables complete and only then passing it along
   * to the observers.
   */
  def combineLatestDelayError[U](other: Observable[U]): Observable[(T, U)] =
    operators.combineLatest(self, other, delayErrors = true)

  /**
   * Takes the elements of the source Observable and emits the maximum value,
   * after the source has completed.
   */
  def max[U >: T](implicit ev: Ordering[U]): Observable[U] =
    operators.math.max(this : Observable[U])

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the maximum key value, where the key is generated by the given function `f`.
   */
  def maxBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
    operators.math.maxBy(this)(f)(ev)

  /**
   * Takes the elements of the source Observable and emits the minimum value,
   * after the source has completed.
   */
  def min[U >: T](implicit ev: Ordering[U]): Observable[U] =
    operators.math.min(this : Observable[U])

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the minimum key value, where the key is generated by the given function `f`.
   */
  def minBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
    operators.math.minBy(this)(f)

  /**
   * Given a source that emits numeric values, the `sum` operator
   * sums up all values and at onComplete it emits the total.
   */
  def sum[U >: T](implicit ev: Numeric[U]): Observable[U] =
    operators.math.sum(this : Observable[U])

  /**
   * Suppress the duplicate elements emitted by the source Observable.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct: Observable[T] =
    operators.distinct.distinct(this)

  /**
   * Given a function that returns a key for each element emitted by
   * the source Observable, suppress duplicates items.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct[U](fn: T => U): Observable[T] =
    operators.distinct.distinctBy(this)(fn)

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged: Observable[T] =
    operators.distinct.untilChanged(this)

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged[U](fn: T => U): Observable[T] =
    operators.distinct.untilChangedBy(this)(fn)

  /**
   * Returns a new Observable that uses the specified
   * `Scheduler` for initiating the subscription.
   */
  def subscribeOn(s: Scheduler): Observable[T] = {
    Observable.create(o => s.execute(unsafeSubscribe(o)))
  }

  /**
   * Converts the source Observable that emits `T` into an Observable
   * that emits `Notification[T]`.
   *
   * NOTE: `onComplete` is still emitted after an `onNext(OnComplete)` notification
   * however an `onError(ex)` notification is emitted as an `onNext(OnError(ex))`
   * followed by an `onComplete`.
   */
  def materialize: Observable[Notification[T]] =
    operators.materialize(self)

  /**
   * Utility that can be used for debugging purposes.
   */
  def dump(prefix: String, out: PrintStream = System.out): Observable[T] =
    operators.debug.dump(self, prefix, out)

  /**
   * Repeats the items emitted by this Observable continuously. It caches the generated items until `onComplete`
   * and repeats them ad infinitum. On error it terminates.
   */
  def repeat: Observable[T] =
    operators.repeat(self)

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers).
   */
  def multicast[R](subject: Subject[T, R])(implicit s: Scheduler): ConnectableObservable[R] =
    ConnectableObservable(this, subject)

  /**
   * Forces a buffered asynchronous boundary.
   *
   * Internally it wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.BufferedSubscriber BufferedSubscriber]].
   *
   * Normally Monifu's implementation guarantees that events are not emitted concurrently,
   * and that the publisher MUST NOT emit the next event without acknowledgement from the consumer
   * that it may proceed, however for badly behaved publishers, this wrapper provides
   * the guarantee that the downstream [[monifu.reactive.Observer Observer]] given in `subscribe` will not receive
   * concurrent events.
   *
   * WARNING: if the buffer created by this operator is unbounded, it can blow up the process if the data source
   * is pushing events faster than what the observer can consume, as it introduces an asynchronous
   * boundary that eliminates the back-pressure requirements of the data source. Unbounded is the default
   * [[monifu.reactive.BufferPolicy policy]], see [[monifu.reactive.BufferPolicy BufferPolicy]]
   * for options.
   */
  def asyncBoundary[U >: T](policy: BufferPolicy[U] = defaultPolicy): Observable[U] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      unsafeSubscribe(BufferedSubscriber(subscriber.observer, policy))
    }

  /**
   * While the destination observer is busy, drop the incoming events.
   */
  def whileBusyDropEvents: Observable[T] =
    operators.whileBusy.dropEvents(self)

  /**
   * While the destination observer is busy, drop the incoming events.
   * When the downstream recovers, we can signal a special event
   * meant to inform the downstream observer how many events
   * where dropped.
   *
   * @param onOverflow is a function used to build a message that will be
   *                   the first event sent after the observer recovers,
   *                   a function receiving as argument the number of dropped
   *                   messages and thus can be used to inform the downstream
   *                   how many messages it missed.
   */
  def whileBusyDropEventsThenSignalOverflow[U >: T](onOverflow: Long => U): Observable[U] =
    operators.whileBusy.dropEventsThenSignalOverflow(self, onOverflow)

  /**
   * While the destination observer is busy, buffers events then send
   * whatever was buffered in one big batch.
   */
  def whileBusyBufferEvents(bufferSize: Int): Observable[Seq[T]] =
    operators.whileBusy.bufferEvents(self, bufferSize)

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def publish()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(PublishSubject())

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
   */
  def behavior[U >: T](initialValue: U)(implicit s: Scheduler): ConnectableObservable[U] =
    multicast(BehaviorSubject(initialValue))

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def replay()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(ReplaySubject())

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def publishLast()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(AsyncSubject())

  /**
   * Returns an Observable that mirrors the behavior of the source,
   * unless the source is terminated with an `onError`, in which
   * case the streaming of events continues with the specified 
   * backup sequence generated by the given partial function.
   *
   * The created Observable mirrors the behavior of the source
   * in case the source does not end with an error or if the
   * thrown `Throwable` is not matched.
   *
   * NOTE that compared with `onErrorResumeNext` from Rx.NET,
   * the streaming is not resumed in case the source is
   * terminated normally with an `onComplete`.
   *
   * @param pf - a partial function that matches errors with a
   *           backup throwable that is subscribed when the source
   *           throws an error.
   */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Observable[U]]): Observable[U] =
    operators.onError.recoverWith(self, pf)

  /**
   * Returns an Observable that mirrors the behavior of the source,
   * unless the source is terminated with an `onError`, in which
   * case the streaming of events continues with the specified
   * backup sequence.
   *
   * The created Observable mirrors the behavior of the source
   * in case the source does not end with an error.
   *
   * NOTE that compared with `onErrorResumeNext` from Rx.NET,
   * the streaming is not resumed in case the source is
   * terminated normally with an `onComplete`.
   *
   * @param that - a backup sequence that's being subscribed
   *             in case the source terminates with an error.
   */
  def onErrorFallbackTo[U >: T](that: => Observable[U]): Observable[U] =
    operators.onError.fallbackTo(self, that)

  /**
   * Returns an Observable that mirrors the behavior of the source,
   * unless the source is terminated with an `onError`, in which case
   * it tries subscribing to the source again in the hope that
   * it will complete without an error.
   *
   * NOTE: The number of retries is unlimited, so something like
   * `Observable.error(new RuntimeException).onErrorRetryUnlimited` will loop
   * forever.
   */
  def onErrorRetryUnlimited: Observable[T] =
    operators.onError.retryUnlimited(self)

  /**
   * Returns an Observable that mirrors the behavior of the source,
   * unless the source is terminated with an `onError`, in which case
   * it tries subscribing to the source again in the hope that
   * it will complete without an error.
   *
   * The number of retries is limited by the specified `maxRetries`
   * parameter, so for an Observable that always ends in error the
   * total number of subscriptions that will eventually happen is
   * `maxRetries + 1`.
   */
  def onErrorRetry(maxRetries: Long): Observable[T] =
    operators.onError.retryCounted(self, maxRetries)

  /**
   * Returns an Observable that mirrors the behavior of the source,
   * unless the source is terminated with an `onError`, in which case
   * it tries subscribing to the source again in the hope that
   * it will complete without an error.
   *
   * The given predicate establishes if the subscription should be
   * retried or not.
   */
  def onErrorRetryIf(p: Throwable => Boolean): Observable[T] =
    operators.onError.retryIf(self, p)

  /**
   * Returns an Observable that mirrors the source Observable but
   * applies a timeout policy for each emitted item. If the next item
   * isn't emitted within the specified timeout duration starting from
   * its predecessor, the resulting Observable terminates and notifies
   * observers of a TimeoutException.
   *
   * @param timeout maximum duration between emitted items before
   *                a timeout occurs
   */
  def timeout(timeout: FiniteDuration): Observable[T] =
    operators.timeout.emitError(self, timeout)

  /**
   * Returns an Observable that mirrors the source Observable but
   * applies a timeout policy for each emitted item. If the next item
   * isn't emitted within the specified timeout duration starting from
   * its predecessor, the resulting Observable begins instead to
   * mirror a backup Observable.
   *
   * @param timeout maximum duration between emitted items before
   *                a timeout occurs
   * @param backup is the backup observable to subscribe to
   *               in case of a timeout
   */
  def timeout[U >: T](timeout: FiniteDuration, backup: Observable[U]): Observable[U] =
    operators.timeout.switchToBackup(self, timeout, backup)

  /**
   * Given a function that transforms an `Observable[T]` into an `Observable[U]`,
   * it transforms the source observable into an `Observable[U]`.
   */
  def lift[U](f: Observable[T] => Observable[U]): Observable[U] =
    f(self)

  /**
   * Returns the first generated result as a Future and then cancels
   * the subscription.
   */
  def asFuture(implicit s: Scheduler): Future[Option[T]] = {
    val promise = Promise[Option[T]]()

    head.unsafeSubscribe(new Observer[T] {
      def onNext(elem: T) = {
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

    promise.future
  }

  /**
   * Subscribes to the source `Observable` and foreach element emitted by the source
   * it executes the given callback.
   */
  def foreach(cb: T => Unit)(implicit s: Scheduler): Unit =
    unsafeSubscribe(new Observer[T] {
      def onNext(elem: T) =
        try { cb(elem); Continue } catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }

      def onComplete() = ()
      def onError(ex: Throwable) = {
        s.reportFailure(ex)
      }
    })
}

object Observable {
  /**
   * Observable constructor for creating an [[Observable]] from the specified function.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/create.png" />
   *
   * Example: {{{
   *   import monifu.reactive._
   *   import monifu.reactive.Ack.Continue
   *   import concurrent.ExecutionContext
   *
   *   def emit[T](elem: T, nrOfTimes: Int)(implicit s: Scheduler): Observable[T] =
   *     Observable.create { observer =>
   *       def loop(times: Int): Unit =
   *         ec.execute(new Runnable {
   *           def run() = {
   *             if (times > 0)
   *               observer.onNext(elem).onSuccess {
   *                 case Continue => loop(times - 1)
   *               }
   *             else
   *               observer.onComplete()
   *           }
   *         })
   *
   *       loop(nrOfTimes)
   *     }
   *
   *   // usage sample
   *   import concurrent.ExecutionContext.Implicits.global

   *   emit(elem=30, nrOfTimes=3).dump("Emit").subscribe()
   *   //=> 0: Emit-->30
   *   //=> 1: Emit-->30
   *   //=> 2: Emit-->30
   *   //=> 3: Emit completed
   * }}}
   */
  def create[T](f: Subscriber[T] => Unit): Observable[T] = {
    new Observable[T] {
      def subscribeFn(subscriber: Subscriber[T]): Unit =
        try f(subscriber) catch {
          case NonFatal(ex) =>
            subscriber.observer.onError(ex)
        }
    }
  }

  /**
   * Creates an observable that doesn't emit anything, but immediately calls `onComplete`
   * instead.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/empty.png" />
   */
  def empty: Observable[Nothing] =
    builders.unit.empty

  /**
   * Creates an Observable that only emits the given ''a''
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/unit.png" />
   */
  def unit[A](elem: A): Observable[A] =
    builders.unit.one(elem)

  /**
   * Creates an Observable that emits an error.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/error.png" />
   */
  def error(ex: Throwable): Observable[Nothing] =
    builders.unit.error(ex)

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/never.png"" />
   */
  def never: Observable[Nothing] =
    builders.unit.never

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time. The given `period` of time acts as a fixed delay
   * between subsequent events.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param delay the delay between two subsequent events
   */
  def intervalWithFixedDelay(delay: FiniteDuration): Observable[Long] =
    builders.interval.withFixedDelay(delay)

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time. The given `period` of time acts as a fixed delay
   * between subsequent events.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param delay the delay between two subsequent events
   */
  def interval(delay: FiniteDuration): Observable[Long] =
    intervalWithFixedDelay(delay)

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) at a fixed rate,
   * as given by the specified `period`. The time it takes to process an `onNext` event gets
   * subtracted from the specified `period` and thus the created observable tries to emit events
   * spaced by the given time interval, regardless of how long the processing of `onNext` takes.
   *
   * For example, an invocation like this:
   * {{{
   *   Observable.intervalAtFixedRate(1.second)
   * }}}
   *
   * Is roughly equivalent to this:
   * {{{
   *   import monifu.concurrent.extensions._
   *
   *   Observable.range(0, Long.MaxValue)
   *     .flatMap(x => Future(x).withMinDuration(1.second))
   * }}}
   *
   * @param period is the period of time the observable waits between 2 subsequent `onNext` events
   */
  def intervalAtFixedRate(period: FiniteDuration): Observable[Long] =
    builders.interval.atFixedRate(period)

  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def repeat[T](elems: T*): Observable[T] =
    builders.repeat(elems : _*)

  /**
   * Creates an Observable that emits items in the given range.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/range.png" />
   *
   * @param from the range start
   * @param until the range end
   * @param step increment step, either positive or negative
   */
  def range(from: Long, until: Long, step: Long = 1L): Observable[Long] =
    builders.range(from, until, step)

  /**
   * Creates an Observable that emits the given elements.
   *
   * Usage sample: {{{
   *   val obs = Observable(1, 2, 3, 4)
   *
   *   obs.dump("MyObservable").subscribe()
   *   //=> 0: MyObservable-->1
   *   //=> 1: MyObservable-->2
   *   //=> 2: MyObservable-->3
   *   //=> 3: MyObservable-->4
   *   //=> 4: MyObservable completed
   * }}}
   */
  def apply[T](elems: T*): Observable[T] = {
    fromIterable(elems)
  }

  /**
   * Converts a Future to an Observable.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def fromFuture[T](future: Future[T]): Observable[T] =
    builders.from.future(future)

  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def fromIterable[T](iterable: Iterable[T]): Observable[T] =
    builders.from.iterable(iterable)

  /**
   * Creates an Observable that emits the given elements exactly.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](elems: T*): Observable[T] =
    builders.from.iterable(elems)

  /**
   * Create an Observable that emits a single item after a given delay.
   */
  def unitDelayed[T](delay: FiniteDuration, unit: T): Observable[T] =
    builders.unit.oneDelayed(delay, unit)

  /**
   * Create an Observable that repeatedly emits the given `item`, until
   * the underlying Observer cancels.
   */
  def timerRepeated[T](initialDelay: FiniteDuration, period: FiniteDuration, unit: T): Observable[T] =
    builders.timer.repeated(initialDelay, period, unit)

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).concat

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   * Delays errors until the end.
   */
  def flattenDelayError[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).concatDelayError

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def merge[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).merge()

  /**
   * Merges the given list of ''observables'' into a single observable.
   * Delays errors until the end.
   */
  def mergeDelayError[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).mergeDelayError()


  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def concat[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).concat

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   * Delays errors until the end.
   */
  def concatDelayError[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).concatDelayError

  /**
   * Creates a new Observable from two observables,
   * by emitting elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2](obs1: Observable[T1], obs2: Observable[T2]): Observable[(T1,T2)] =
    obs1.zip(obs2)

  /**
   * Creates a new Observable from three observables,
   * by emitting elements combined in tuples of 3 elements. If one of the Observable emits fewer
   * events than the others, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2, T3](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3]): Observable[(T1, T2, T3)] =
    obs1.zip(obs2).zip(obs3).map { case ((t1, t2), t3) => (t1, t2, t3) }

  /**
   * Creates a new Observable from three observables,
   * by emitting elements combined in tuples of 4 elements. If one of the Observable emits fewer
   * events than the others, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2, T3, T4](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3], obs4: Observable[T4]): Observable[(T1, T2, T3, T4)] =
    obs1.zip(obs2).zip(obs3).zip(obs4).map { case (((t1, t2), t3), t4) => (t1, t2, t3, t4) }

  /**
   * Creates a combined observable from 2 source observables.
   *
   * This operator behaves in a similar way to [[zip]], but while `zip` emits items
   * only when all of the zipped source Observables have emitted a previously unzipped item,
   * `combine` emits an item whenever any of the source Observables emits
   * an item (so long as each of the source Observables has emitted at least one item).
   */
  def combineLatest[T1, T2](first: Observable[T1], second: Observable[T2]): Observable[(T1,T2)] = {
    first.combineLatest(second)
  }

  /**
   * Creates a combined observable from 3 source observables.
   *
   * This operator behaves in a similar way to [[zip]], but while `zip` emits items
   * only when all of the zipped source Observables have emitted a previously unzipped item,
   * `combine` emits an item whenever any of the source Observables emits
   * an item (so long as each of the source Observables has emitted at least one item).
   */
  def combineLatest[T1, T2, T3]
      (first: Observable[T1], second: Observable[T2], third: Observable[T3]): Observable[(T1,T2,T3)] = {

    first.combineLatest(second).combineLatest(third)
      .map { case ((t1, t2), t3) => (t1, t2, t3) }
  }

  /**
   * Creates a combined observable from 4 source observables.
   *
   * This operator behaves in a similar way to [[zip]], but while `zip` emits items
   * only when all of the zipped source Observables have emitted a previously unzipped item,
   * `combine` emits an item whenever any of the source Observables emits
   * an item (so long as each of the source Observables has emitted at least one item).
   */
  def combineLatest[T1, T2, T3, T4]
      (first: Observable[T1], second: Observable[T2],
       third: Observable[T3], fourth: Observable[T4]): Observable[(T1, T2, T3, T4)] = {

    first.combineLatest(second).combineLatest(third).combineLatest(fourth)
      .map { case (((t1, t2), t3), t4) => (t1, t2, t3, t4) }
  }

  /**
   * Given a list of source Observables, emits all of the items from the first of
   * these Observables to emit an item and cancel the rest.
   */
  def amb[T](source: Observable[T]*): Observable[T] =
    builders.amb(source : _*)

  /**
   * Implicit conversion from Future to Observable.
   */
  implicit def FutureIsObservable[T](future: Future[T]): Observable[T] =
    Observable.fromFuture(future)

  /**
   * Implicit conversion from Observable to Publisher.
   */
  implicit def ObservableIsPublisher[T](source: Observable[T])(implicit s: Scheduler): Publisher[T] =
    source.publisher
}
