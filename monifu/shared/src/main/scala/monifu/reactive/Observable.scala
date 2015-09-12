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
import java.util.concurrent.Callable
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.OverflowStrategy.{default => defaultStrategy}
import monifu.reactive.internals._
import monifu.reactive.observables.{CachedObservable, ConnectableObservable, GroupedObservable}
import monifu.reactive.observers._
import monifu.reactive.subjects.{AsyncSubject, BehaviorSubject, PublishSubject, ReplaySubject}
import org.reactivestreams.{Publisher => RPublisher, Subscriber => RSubscriber}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.util.control.NonFatal


/**
 * The Observable interface in the Rx pattern.
 *
 * ==Interface==
 *
 * An Observable is characterized by a `onSubscribe` method that needs
 * to be implemented. In simple terms, an Observable might as well be
 * just a function like:
 * {{{
 *   type Observable[+T] = Subscriber[T] => Unit
 * }}}
 *
 * In other words an Observable is something that provides a
 * side-effecting function that can connect a [[Subscriber]] to a
 * stream of data. A `Subscriber` is a cross between an [[Observer]]
 * and a [[monifu.concurrent.Scheduler Scheduler]].  We need a
 * `Scheduler` when calling `subscribe` because that's when the
 * side-effects happen and a context capable of scheduling tasks for
 * asynchronous execution is needed. An [[Observer]] on the other hand
 * is the interface implemented by consumer and that receives events
 * according to the Rx grammar.
 *
 * On `onSubscribe`, because we need the interesting operators and
 * the polymorphic behavior provided by OOP, the Observable is
 * being described as an interface that has to be implemented:
 * {{{
 *   class MySampleObservable(unit: Int) extends Observable[Int] {
 *     def onSubscribe(sub: Subscriber[Int]): Unit = {
 *       implicit val s = sub.scheduler
 *       // note we must apply back-pressure
 *       // when calling `onNext`
 *       sub.onNext(unit).onComplete {
 *         case Success(Cancel) =>
 *           () // do nothing
 *         case Success(Continue) =>
 *           sub.onComplete()
 *         case Failure(ex) =>
 *           sub.onError(ex)
 *       }
 *     }
 *   }
 * }}}
 *
 * Of course, you don't need to inherit from this trait, as you can just
 * use [[Observable.create]], the following example being equivalent
 * to the above:
 * {{{
 *   Observable.create[Int] { sub =>
 *     implicit val s = sub.scheduler
 *     // note we must apply back-pressure
 *     // when calling `onNext`
 *     sub.onNext(unit).onComplete {
 *       case Success(Cancel) =>
 *         () // do nothing
 *       case Success(Continue) =>
 *         sub.onComplete()
 *       case Failure(ex) =>
 *         sub.onError(ex)
 *     }
 *   }
 * }}}
 *
 * The above is describing how to create your own Observables, however
 * Monifu already provides already made utilities in the
 * [[Observable$ Observable companion object]]. For example, to
 * periodically make a request to a web service, you could do it like
 * this:
 * {{{
 *   // just some http client
 *   import play.api.libs.ws._
 *
 *   // triggers an auto-incremented number every second
 *   Observable.intervalAtFixedRate(1.second)
 *     .flatMap(_ => WS.request(s"http://some.endpoint.com/request").get())
 * }}}
 *
 * As you might notice, in the above example we are doing
 * [[Observable!.flatMap]] on an Observable that emits `Future`
 * instances. And it works, because Monifu considers Scala's Futures
 * to be just a subset of Observables, see the automatic
 * [[Observable.FutureIsObservable FutureIsObservable]] conversion that
 * it defines. Or you could just use [[Observable.fromFuture]] for
 * explicit conversions, an Observable builder available
 * [[Observable$ amongst others]].
 *
 * ==Contract==
 *
 * Observables must obey Monifu's contract, this is why if you get away
 * with already built and tested observables, that would be better than
 * implementing your own by means of inheriting the interface or by using
 * [[Observable.create create]]. The contract is this:
 *
 *   - the supplied `onSubscribe` method MUST NOT throw exceptions, any
 *     unforeseen errors that happen in user-code must be emitted to
 *     the observers and the streaming closed
 *   - events MUST follow this grammar: `onNext* (onComplete | onError)`
 *     - a data source can emit zero or many `onNext` events
 *     - the stream can be infinite, but when the stream is closed
 *       (and not canceled by the observer), then
 *       it always emits a final `onComplete` or `onError`
 *   - MUST apply back-pressure when emitting events, which means that sending
 *     events is always done in response to demand signaled by observers and
 *     that observers should only receive events in response to that signaled
 *     demand
 *     - emitting a new `onNext` event must happen only after the previous
 *       `onNext` completed with a [[Ack.Continue Continue]]
 *     - streaming must stop immediately after an `onNext` event
 *       is signaling a [[Ack.Cancel Cancel]]
 *     - back-pressure must be applied for the final events as well,
 *       so `onComplete` and `onError` must happen only after the previous
 *       `onNext` was completed with a [[Ack.Continue Continue]]
 *       acknowledgement
 *     - the first `onNext` event can be sent directly, since there are no
 *       previous events
 *     - if there are no previous `onNext` events, then streams can be
 *       closed with `onComplete` and `onError` directly
 *     - if buffering of events happens, it is acceptable for events
 *       to get dropped when `onError` happens such that its delivery
 *       is prioritized
 *
 * ===On Dealing with the contract===
 *
 * Back-pressure means in essence that the speed with which the data-source
 * produces events is adjusted to the speed with which the consumer consumes.
 *
 * For example, lets say we want to feed an iterator into an observer,
 * similar to what we are doing in [[Observer.Extensions.feed(iterable* Observer.feed]],
 * we might build a loop like this:
 * {{{
 *   /** Transforms any Iterable into an Observable */
 *   def fromIterator[T](iterable: Iterable[T]): Observable[T] =
 *     Observable.create { sub =>
 *       implicit val s = sub.scheduler
 *       loop(sub.observer, iterable.iterator).onComplete {
 *         case Success(Cancel) =>
 *           () // do nothing
 *         case Success(Continue) =>
 *           sub.onComplete()
 *         case Failed(ex) =>
 *           reportError(ex)
 *       }
 *     }
 *
 *   private def loop[T](o: Observer[T], iterator: Iterator[T])
 *     (implicit s: Scheduler): Future[Ack] = {
 *
 *     try {
 *       if (iterator.hasNext) {
 *         val next = iterator.next()
 *         // signaling event, applying back-pressure
 *         o.onNext(next).flatMap {
 *           case Cancel => Cancel
 *           case Continue =>
 *             // signal next event (recursive, but async)
 *             loop(o, iterator)
 *         }
 *       }
 *       else {
 *         // nothing left to do, and because we are implementing
 *         // Observer.feed, the final acknowledgement is a `Continue`
 *         // assuming that the observer hasn't canceled or failed
 *         Continue
 *       }
 *     }
 *     catch {
 *       case NonFatal(ex) =>
 *         reportError(ex)
 *     }
 *   }
 *
 *   private def reportError[T](o: Observer[T], ex: Throwable): Cancel =
 *     try o.onError(ex) catch {
 *       case NonFatal(err) =>
 *         // oops, onError failed, trying to
 *         // report it somewhere
 *         s.reportFailure(ex)
 *         s.reportFailure(err)
 *         Cancel
 *     }
 * }}}
 *
 * There are cases in which the data-source can't be slowed down in response
 * to the demand signaled through back-pressure. For such cases buffering
 * is needed.
 *
 * For example to "imperatively" build an Observable, we could use channels:
 * {{{
 *   val channel = PublishChannel[Int](OverflowStrategy.DropNew(bufferSize = 100))
 *
 *   // look mum, no back-pressure concerns
 *   channel.pushNext(1)
 *   channel.pushNext(2)
 *   channel.pushNext(3)
 *   channel.pushComplete()
 * }}}
 *
 * In Monifu a [[Channel]] is much like a [[Subject]], meaning that it can be
 * used to construct observables, except that a `Channel` has a buffer
 * attached and IS NOT an `Observer` (like the `Subject` is). In Monifu
 * (compared to Rx implementations) [[Subject Subjects]] are subject to
 * back-pressure concerns as well, so they can't be used in an imperative way,
 * like described above, hence the need for Channels.
 *
 * Or for more serious and lower level jobs, you can simply take an
 * `Observer` and wrap it into a
 * [[monifu.reactive.observers.BufferedSubscriber BufferedSubscriber]].
 *
 * @see [[monifu.reactive.Observer Observer]], the interface that must be
 *     implemented by consumers
 * @see [[monifu.concurrent.Scheduler Scheduler]], our enhanced `ExecutionContext`
 * @see [[monifu.reactive.Subscriber Subscriber]], the cross between an
 *     [[Observer]] and a [[monifu.concurrent.Scheduler Scheduler]]
 * @see [[monifu.concurrent.Cancelable Cancelable]], the type returned by higher
 *     level `subscribe` variants and that can be used to cancel subscriptions
 * @see [[monifu.reactive.Subject Subject]], which are both Observables and Observers
 * @see [[monifu.reactive.Channel Channel]], which are meant for imperatively building
 *     Observables without back-pressure concerns
 *
 * @define concatDescription Concatenates the sequence
 *         of Observables emitted by the source into one Observable,
 *         without any transformation.
 *
 *         You can combine the items emitted by multiple Observables
 *         so that they act like a single Observable by using this
 *         method.
 *
 *         The difference between the `concat` operation and
 *         [[Observable!.merge[U](implicit* merge]] is that `concat` cares about
 *         ordering of emitted items (e.g. all items emitted by the
 *         first observable in the sequence will come before the
 *         elements emitted by the second observable), whereas
 *         `merge` doesn't care about that (elements get emitted as
 *         they come). Because of back-pressure applied to
 *         observables, [[Observable!.concat]] is safe to use in all
 *         contexts, whereas `merge` requires buffering.
 *
 * @define concatReturn an Observable that emits items that are the result of
 *         flattening the items emitted by the Observables emitted
 *         by `this`
 *
 * @define mergeMapDescription Creates a new Observable by applying a
 *         function that you supply to each item emitted by the source
 *         Observable, where that function returns an Observable, and then
 *         merging those resulting Observables and emitting the
 *         results of this merger.
 *
 *         This function is the equivalent of `observable.map(f).merge`.
 *
 *         The difference between [[Observable!.concat concat]] and
 *         `merge` is that `concat` cares about ordering of emitted
 *         items (e.g. all items emitted by the first observable in
 *         the sequence will come before the elements emitted by the
 *         second observable), whereas `merge` doesn't care about that
 *         (elements get emitted as they come). Because of
 *         back-pressure applied to observables, [[Observable!.concat concat]]
 *         is safe to use in all contexts, whereas
 *         [[Observable!.merge[U](implicit* merge]] requires buffering.
 *
 * @define mergeMapReturn an Observable that emits the result of applying the
 *         transformation function to each item emitted by the source
 *         Observable and merging the results of the Observables
 *         obtained from this transformation.
 *
 * @define mergeDescription Merges the sequence of Observables emitted by
 *         the source into one Observable, without any transformation.
 *
 *         You can combine the items emitted by multiple Observables
 *         so that they act like a single Observable by using this
 *         method.
 *
 * @define mergeReturn an Observable that emits items that are the
 *         result of flattening the items emitted by the Observables
 *         emitted by `this`
 *
 * @define overflowStrategyParam the [[OverflowStrategy overflow strategy]]
 *         used for buffering, which specifies what to do in case we're
 *         dealing with a slow consumer - should an unbounded buffer be used,
 *         should back-pressure be applied, should the pipeline drop newer or
 *         older events, should it drop the whole buffer? See
 *         [[OverflowStrategy]] for more details
 *
 * @define onOverflowParam a function that is used for signaling a special
 *         event used to inform the consumers that an overflow event
 *         happened, function that receives the number of dropped events as
 *         a parameter (see [[OverflowStrategy.Evicted]])
 *
 * @define delayErrorsDescription This version
 *         is reserving onError notifications until all of the
 *         Observables complete and only then passing the issued
 *         errors(s) along to the observers. Note that the streamed
 *         error is a [[monifu.reactive.exceptions.CompositeException CompositeException]],
 *         since multiple errors from multiple streams can happen.
 *
 * @define defaultOverflowStrategy this operation needs to do buffering
 *         and by not specifying an [[OverflowStrategy]], the
 *         [[OverflowStrategy.default default strategy]] is being
 *         used.
 *
 * @define switchDescription Convert an Observable that emits Observables
 *         into a single Observable that emits the items emitted by the
 *         most-recently-emitted of those Observables.
 *
 * @define switchMapDescription Returns a new Observable that emits the items
 *         emitted by the Observable most recently generated by the mapping
 *         function.
 *
 * @define asyncBoundaryDescription Forces a buffered asynchronous boundary.
 *
 *         Internally it wraps the observer implementation given to
 *         `onSubscribe` into a
 *         [[monifu.reactive.observers.BufferedSubscriber BufferedSubscriber]].
 *
 *         Normally Monifu's implementation guarantees that events
 *         are not emitted concurrently, and that the publisher MUST
 *         NOT emit the next event without acknowledgement from the
 *         consumer that it may proceed, however for badly behaved
 *         publishers, this wrapper provides the guarantee that the
 *         downstream [[monifu.reactive.Observer Observer]] given in
 *         `subscribe` will not receive concurrent events.
 *
 *         WARNING: if the buffer created by this operator is
 *         unbounded, it can blow up the process if the data source
 *         is pushing events faster than what the observer can
 *         consume, as it introduces an asynchronous boundary that
 *         eliminates the back-pressure requirements of the data
 *         source. Unbounded is the default
 *         [[monifu.reactive.OverflowStrategy overflowStrategy]], see
 *         [[monifu.reactive.OverflowStrategy OverflowStrategy]] for
 *         options.
 */
trait Observable[+T] { self =>
  /**
   * Characteristic function for an `Observable` instance, that
   * creates the subscription and that eventually starts the streaming
   * of events to the given [[Observer]], being meant to be overridden
   * in custom combinators or in classes implementing Observable.
   *
   * This function is "unsafe" to call because it does not protect the
   * calls to the given [[Observer]] implementation in regards to
   * unexpected exceptions that violate the contract, therefore the
   * given instance must respect its contract and not throw any
   * exceptions when the observable calls `onNext`, `onComplete` and
   * `onError`. If it does, then the behavior is undefined.
   *
   * @see [[Observable.subscribe(observer* subscribe]].
   */
  def onSubscribe(subscriber: Subscriber[T]): Unit

  /**
   * Subscribes to the stream.
   *
   * This function is "unsafe" to call because it does not protect the
   * calls to the given [[Observer]] implementation in regards to
   * unexpected exceptions that violate the contract, therefore the
   * given instance must respect its contract and not throw any
   * exceptions when the observable calls `onNext`, `onComplete` and
   * `onError`. If it does, then the behavior is undefined.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] that respects
   *                 the Monifu Rx contract
   *
   * @param s is the [[monifu.concurrent.Scheduler Scheduler]]
   *          used for creating the subscription
   */
  def onSubscribe(observer: Observer[T])(implicit s: Scheduler): Unit = {
    onSubscribe(Subscriber(observer, s))
  }

  /**
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
   */
  def subscribe(subscriber: Subscriber[T]): BooleanCancelable = {
    val cancelable = BooleanCancelable()
    takeWhileNotCanceled(cancelable).onSubscribe(SafeSubscriber[T](subscriber))
    cancelable
  }

  /**
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
   */
  def subscribe(observer: Observer[T])(implicit s: Scheduler): BooleanCancelable = {
    subscribe(Subscriber(observer, s))
  }

  /**
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
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
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
   */
  def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit)(implicit s: Scheduler): BooleanCancelable =
    subscribe(nextFn, errorFn, () => ())

  /**
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
   */
  def subscribe()(implicit s: Scheduler): Cancelable =
    subscribe(elem => Continue)

  /**
   * Subscribes to the stream.
   *
   * @return a subscription that can be used to cancel the streaming.
   */
  def subscribe(nextFn: T => Future[Ack])(implicit s: Scheduler): BooleanCancelable =
    subscribe(nextFn, error => s.reportFailure(error), () => ())

  /**
   * Wraps this Observable into a `org.reactivestreams.Publisher`.
   * See the [[http://www.reactive-streams.org/ Reactive Streams]]
   * protocol that Monifu implements.
   */
  def toReactive[U >: T](implicit s: Scheduler): RPublisher[U] =
    new RPublisher[U] {
      def subscribe(subscriber: RSubscriber[_ >: U]): Unit = {
        onSubscribe(SafeSubscriber(Subscriber.fromReactiveSubscriber(subscriber)))
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
   * It's an alias for [[Observable!.concatMapDelayError]].
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
   * It's like [[Observable!.concatMap]], except that the created observable is reserving onError
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
   * $mergeMapDescription
   *
   * @param f - the transformation function
   * @return $mergeMapReturn
   */
  def mergeMap[U](f: T => Observable[U]): Observable[U] =
    map(f).merge

  /**
   * $mergeMapDescription
   *
   * $delayErrorsDescription
   * 
   * @param f - the transformation function
   * @return $mergeMapReturn
   */
  def mergeMapDelayErrors[U](f: T => Observable[U]): Observable[U] =
    map(f).mergeDelayErrors

  /**
   * Alias for [[Observable!.concat]].
   *
   * $concatDescription
   *
   * @return $concatReturn
   */
  def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] =
    concat

  /**
   * Alias for [[Observable!.concatDelayError]].
   *
   * $concatDescription
   * $delayErrorsDescription
   *
   * @return $concatReturn
   */
  def flattenDelayError[U](implicit ev: T <:< Observable[U]): Observable[U] =
    concatDelayError

  /**
   * $concatDescription
   *
   * @return $concatReturn
   */
  def concat[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.concat(self, delayErrors = false)

  /**
   * $concatDescription
   *
   * $delayErrorsDescription
   *
   * @return $concatReturn
   */
  def concatDelayError[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.flatten.concat(self, delayErrors = true)

  /**
   * $mergeDescription
   *
   * @note $defaultOverflowStrategy
   * @return $mergeReturn
   */
  def merge[U](implicit ev: T <:< Observable[U]): Observable[U] = {
    operators.flatten.merge(self)(defaultStrategy,
      onOverflow = null, delayErrors = false)
  }

  /**
   * $mergeDescription
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @return $mergeReturn
   */
  def merge[U](overflowStrategy: OverflowStrategy)
    (implicit ev: T <:< Observable[U]): Observable[U] = {

    operators.flatten.merge(self)(overflowStrategy,
      onOverflow = null, delayErrors = false)
  }

  /**
   * $mergeDescription
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @param onOverflow - $onOverflowParam
   * @return $mergeReturn
   */
  def merge[U](overflowStrategy: OverflowStrategy.Evicted, onOverflow: Long => U)
    (implicit ev: T <:< Observable[U]): Observable[U] = {

    operators.flatten.merge(self)(overflowStrategy, 
      onOverflow, delayErrors = false)
  }

  /**
   * $mergeDescription
   *
   * $delayErrorsDescription
   * 
   * @note $defaultOverflowStrategy
   * @return $mergeReturn
   */
  def mergeDelayErrors[U](implicit ev: T <:< Observable[U]): Observable[U] = {
    operators.flatten.merge(self)(defaultStrategy, null, delayErrors = true)
  }

  /**
   * $mergeDescription
   *
   * $delayErrorsDescription
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @return $mergeReturn
   */
  def mergeDelayErrors[U](overflowStrategy: OverflowStrategy)
      (implicit ev: T <:< Observable[U]): Observable[U] = {

    operators.flatten.merge(self)(overflowStrategy, null, delayErrors = true)
  }

  /**
   * $mergeDescription
   *
   * $delayErrorsDescription
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @param onOverflow - $onOverflowParam
   * @return $mergeReturn
   */
  def mergeDelayErrors[U](overflowStrategy: OverflowStrategy.Evicted, onOverflow: Long => U)
    (implicit ev: T <:< Observable[U]): Observable[U] = {

    operators.flatten.merge(self)(overflowStrategy, onOverflow, delayErrors = true)
  }
  
  /**
   * $switchDescription
   */
  def switch[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.switch(self)

  /**
   * $switchMapDescription
   */
  def switchMap[U](f: T => Observable[U]): Observable[U] =
    map(f).switch

  /**
   * Alias for [[Observable!.switch]]
   *
   * $switchDescription
   */
  def flattenLatest[U](implicit ev: T <:< Observable[U]): Observable[U] =
    operators.switch(self)

  /**
   * An alias of [[Observable!.switchMap]].
   *
   * $switchMapDescription
   */
  def flatMapLatest[U](f: T => Observable[U]): Observable[U] =
    map(f).flattenLatest

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
  def take(n: Long): Observable[T] =
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
   * @param count the maximum size of each buffer before it should be emitted
   */
  def buffer(count: Int): Observable[Seq[T]] =
    operators.buffer.skipped(self, count, count)

  /**
   * Returns an Observable that emits buffers of items it collects from the
   * source Observable. The resulting Observable emits buffers every `skip`
   * items, each containing `count` items. When the source Observable completes
   * or encounters an error, the resulting Observable emits the current buffer
   * and propagates the notification from the source Observable.
   *
   * There are 3 possibilities:
   *
   * 1. in case `skip == count`, then there are no items dropped and no overlap,
   *    the call being equivalent to `window(count)`
   * 2. in case `skip < count`, then overlap between windows happens, with the
   *    number of elements being repeated being `count - skip`
   * 3. in case `skip > count`, then `skip - count` elements start getting
   *    dropped between windows
   *
   * @param count the maximum size of each buffer before it should be emitted
   * @param skip how many items emitted by the source Observable should be
   *             skipped before starting a new buffer. Note that when skip and
   *             count are equal, this is the same operation as `buffer(count)`
   */
  def buffer(count: Int, skip: Int): Observable[Seq[T]] =
    operators.buffer.skipped(self, count, skip)

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
  def buffer(timespan: FiniteDuration): Observable[Seq[T]] =
    operators.buffer.timed(self, maxCount = 0, timespan = timespan)

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time.
   *
   * The resulting Observable emits connected, non-overlapping buffers, each of
   * a fixed duration specified by the `timespan` argument or a maximum size
   * specified by the `maxSize` argument (whichever is reached first). When the
   * source Observable completes or encounters an error, the resulting
   * Observable emits the current buffer and propagates the notification from
   * the source Observable.
   *
   * @param timespan the interval of time at which it should emit the buffered bundle
   * @param maxSize is the maximum bundle size
   */
  def buffer(timespan: FiniteDuration, maxSize: Int): Observable[Seq[T]] =
    operators.buffer.timed(self, timespan, maxSize)

  /**
   * Periodically subdivide items from an Observable into Observable windows and
   * emit these windows rather than emitting the items one at a time.
   *
   * This variant of window opens its first window immediately. It closes the
   * currently open window and immediately opens a new one whenever the current
   * window has emitted count items. It will also close the currently open
   * window if it receives an onCompleted or onError notification from the
   * source Observable. This variant of window emits a series of non-overlapping
   * windows whose collective emissions correspond one-to-one with those of
   * the source Observable.
   *
   * @param count the bundle size
   */
  def window(count: Int): Observable[Observable[T]] =
    operators.window.skipped(self, count, count)

  /**
   * Returns an Observable that emits windows of items it collects from the
   * source Observable. The resulting Observable emits windows every skip items,
   * each containing no more than count items. When the source Observable
   * completes or encounters an error, the resulting Observable emits the
   * current window and propagates the notification from the source Observable.
   *
   * There are 3 possibilities:
   *
   * 1. in case `skip == count`, then there are no items dropped and no overlap,
   *    the call being equivalent to `window(count)`
   * 2. in case `skip < count`, then overlap between windows happens, with the
   *    number of elements being repeated being `count - skip`
   * 3. in case `skip > count`, then `skip - count` elements start getting
   *    dropped between windows
   *
   * @param count - the maximum size of each window before it should be emitted
   * @param skip - how many items need to be skipped before starting a new window
   */
  def window(count: Int, skip: Int): Observable[Observable[T]] =
    operators.window.skipped(self, count, skip)

  /**
   * Periodically subdivide items from an Observable into Observable windows and
   * emit these windows rather than emitting the items one at a time.
   *
   * The resulting Observable emits connected, non-overlapping windows,
   * each of a fixed duration specified by the timespan argument. When
   * the source Observable completes or encounters an error, the resulting
   * Observable emits the current window and propagates the notification
   * from the source Observable.
   *
   * @param timespan the interval of time at which it should complete the
   *                 current window and emit a new one
   */
  def window(timespan: FiniteDuration): Observable[Observable[T]] =
    operators.window.timed(self, timespan, maxCount = 0)

  /**
   * Periodically subdivide items from an Observable into Observable windows and
   * emit these windows rather than emitting the items one at a time.
   *
   * The resulting Observable emits connected, non-overlapping windows,
   * each of a fixed duration specified by the timespan argument. When
   * the source Observable completes or encounters an error, the resulting
   * Observable emits the current window and propagates the notification
   * from the source Observable.
   *
   * @param timespan the interval of time at which it should complete the
   *                 current window and emit a new one
   * @param maxCount the maximum size of each window
   */
  def window(timespan: FiniteDuration, maxCount: Int): Observable[Observable[T]] =
    operators.window.timed(self, timespan, maxCount)

  /**
   * Groups the items emitted by an Observable according to a specified 
   * criterion, and emits these grouped items as GroupedObservables, 
   * one GroupedObservable per group.
   *
   * Note: A [[monifu.reactive.observables.GroupedObservable GroupedObservable]]
   * will cache the items it is to emit until such time as it is
   * subscribed to. For this reason, in order to avoid memory leaks,
   * you should not simply ignore those GroupedObservables that do not
   * concern you. Instead, you can signal to them that they may
   * discard their buffers by doing something like `source.take(0)`.
   *
   * @param keySelector - a function that extracts the key for each item
   */
  def groupBy[K](keySelector: T => K): Observable[GroupedObservable[K,T]] =
    operators.groupBy.apply(self, OverflowStrategy.Unbounded, keySelector)

  /**
   * Groups the items emitted by an Observable according to a specified
   * criterion, and emits these grouped items as GroupedObservables,
   * one GroupedObservable per group.
   *
   * A [[monifu.reactive.observables.GroupedObservable GroupedObservable]]
   * will cache the items it is to emit until such time as it is
   * subscribed to. For this reason, in order to avoid memory leaks,
   * you should not simply ignore those GroupedObservables that do not
   * concern you. Instead, you can signal to them that they may
   * discard their buffers by doing something like `source.take(0)`.
   *
   * This variant of `groupBy` specifies a `keyBufferSize` representing the
   * size of the buffer that holds our keys. We cannot block when emitting
   * new `GroupedObservable`. So by specifying a buffer size, on overflow
   * the resulting observable will terminate with an onError.
   *
   * @param keySelector - a function that extracts the key for each item
   * @param keyBufferSize - the buffer size used for buffering keys
   */
  def groupBy[K](keyBufferSize: Int, keySelector: T => K): Observable[GroupedObservable[K,T]] =
    operators.groupBy.apply(self, OverflowStrategy.Fail(keyBufferSize), keySelector)

  /**
   * Returns an Observable that emits only the last item emitted by the source
   * Observable during sequential time windows of a specified duration.
   *
   * This differs from [[Observable!.throttleFirst]] in that this ticks along
   * at a scheduled interval whereas `throttleFirst` does not tick, it just
   * tracks passage of time.
   *
   * @param period - duration of windows within which the last item
   *                 emitted by the source Observable will be emitted
   */
  def throttleLast(period: FiniteDuration): Observable[T] =
    sample(period)

  /**
   * Returns an Observable that emits only the first item emitted by the source
   * Observable during sequential time windows of a specified duration.
   *
   * This differs from [[Observable!.throttleLast]] in that this only tracks
   * passage of time whereas `throttleLast` ticks at scheduled intervals.
   *
   * @param interval time to wait before emitting another item after
   *                       emitting the last item
   */
  def throttleFirst(interval: FiniteDuration): Observable[T] =
    operators.throttle.first(self, interval)

  /**
   * Alias for [[Observable!.debounce(timeout* debounce]].
   *
   * Returns an Observable that only emits those items emitted by the source
   * Observable that are not followed by another emitted item within a
   * specified time window.
   *
   * Note: If the source Observable keeps emitting items more frequently than
   * the length of the time window then no items will be emitted by the
   * resulting Observable.
   *
   * @param timeout - the length of the window of time that must pass after the
   *                emission of an item from the source Observable in which that
   *                Observable emits no items in order for the item to be
   *                emitted by the resulting Observable
   */
  def throttleWithTimeout(timeout: FiniteDuration): Observable[T] =
    debounce(timeout)

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
   * Returns an Observable that, when the specified sampler Observable emits an
   * item or completes, emits the most recently emitted item (if any) emitted
   * by the source Observable since the previous emission from the sampler
   * Observable.
   *
   * Use the sample() method to periodically look at an Observable
   * to see what item it has most recently emitted since the previous
   * sampling. Note that if the source Observable has emitted no
   * items since the last time it was sampled, the Observable that
   * results from the sample( ) operator will emit no item.
   *
   * @param sampler - the Observable to use for sampling the source Observable
   */
  def sample[U](sampler: Observable[U]): Observable[T] =
    operators.sample.once(self, sampler)

  /**
   * Emit the most recent items emitted by an Observable within periodic time
   * intervals. If no new value has been emitted since the last time it
   * was sampled, the emit the last emitted value anyway.
   *
   * Also see [[Observable!.sample(delay* Observable.sample]].
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
   * Also see [[Observable!.sample(initial* sample]].
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
   * Returns an Observable that, when the specified sampler Observable emits an
   * item or completes, emits the most recently emitted item (if any) emitted
   * by the source Observable since the previous emission from the sampler
   * Observable. If no new value has been emitted since the last time it
   * was sampled, the emit the last emitted value anyway.
   *
   * @see [[Observable!.sample[U](sampler* Observable.sample]]
   *
   * @param sampler - the Observable to use for sampling the source Observable
   */
  def sampleRepeated[U](sampler: Observable[U]): Observable[T] =
    operators.sample.repeated(self, sampler)

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
   *
   * @see [[Observable.echoOnce echoOnce]] for a similar operator that also
   *     mirrors the source observable
   */
  def debounce(timeout: FiniteDuration): Observable[T] =
    operators.debounce.timeout(self, timeout, repeat = false)

  /**
   * Emits the last item from the source Observable if a particular
   * timespan has passed without it emitting another item,
   * and keeps emitting that item at regular intervals until
   * the source breaks the silence.
   *
   * So compared to regular [[Observable!.debounce debounce]]
   * it keeps emitting the last item of the source.
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then no items will be emitted
   * by the resulting Observable.
   *
   * @param period the length of the window of time that must pass after
   *               the emission of an item from the source Observable in which
   *               that Observable emits no items in order for the item to
   *               be emitted by the resulting Observable at regular intervals,
   *               also determined by period
   *
   * @see [[Observable.echoRepeated echoRepeated]] for a similar operator
   *     that also mirrors the source observable
   */
  def debounceRepeated(period: FiniteDuration): Observable[T] =
    operators.debounce.timeout(self, period, repeat = true)

  /**
   * Doesn't emit anything until a `timeout` period passes without
   * the source emitting anything. When that timeout happens,
   * we subscribe to the observable generated by the given function,
   * an observable that will keep emitting until the source will
   * break the silence by emitting another event.
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window, then no items will be emitted
   * by the resulting Observable.
   *
   * @param f is a function that receives the last element generated by the
   *          source, generating an observable to be subscribed when the
   *          source is timing out
   *
   * @param timeout the length of the window of time that must pass after
   *                the emission of an item from the source Observable in which
   *                that Observable emits no items in order for the item to
   *                be emitted by the resulting Observable
   */
  def debounce[U](timeout: FiniteDuration, f: T => Observable[U]): Observable[U] =
    operators.debounce.flatten(self, timeout, f)

  /**
   * Only emit an item from an Observable if a particular
   * timespan has passed without it emitting another item,
   * a timespan indicated by the completion of an observable
   * generated the `selector` function.
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then no items will be emitted
   * by the resulting Observable.
   *
   * @param selector function to retrieve a sequence that indicates the
   *                 throttle duration for each item
   */
  def debounce(selector: T => Observable[Any]): Observable[T] =
    operators.debounce.bySelector(self, selector)

  /**
   * Only emit an item from an Observable if a particular
   * timespan has passed without it emitting another item,
   * a timespan indicated by the completion of an observable
   * generated the `selector` function.
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then no items will be emitted
   * by the resulting Observable.
   *
   * @param selector function to retrieve a sequence that indicates the
   *                 throttle duration for each item
   *
   * @param f is a function that receives the last element generated by the
   *          source, generating an observable to be subscribed when the
   *          source is timing out
   */
  def debounce[U](selector: T => Observable[Any], f: T => Observable[U]): Observable[U] =
    operators.debounce.flattenBySelector(self, selector, f)

  /**
   * Mirror the source observable as long as the source keeps emitting items,
   * otherwise if `timeout` passes without the source emitting anything new
   * then the observable will emit the last item.
   *
   * This is the rough equivalent of:
   * {{{
   *   Observable.merge(source, source.debounce(period))
   * }}}
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then the resulting observable
   * will mirror the source exactly.
   *
   * @param timeout the window of silence that must pass in order for the
   *                observable to echo the last item
   */
  def echoOnce(timeout: FiniteDuration): Observable[T] =
    operators.echo.apply(self, timeout, onlyOnce = true)

  /**
   * Mirror the source observable as long as the source keeps emitting items,
   * otherwise if `timeout` passes without the source emitting anything new
   * then the observable will start emitting the last item repeatedly.
   *
   * This is the rough equivalent of:
   * {{{
   *   source.switch { e =>
   *     e +: Observable.intervalWithFixedDelay(delay, delay)
   *   }
   * }}}
   *
   * Note: If the source Observable keeps emitting items more frequently
   * than the length of the time window then the resulting observable
   * will mirror the source exactly.
   *
   * @param timeout the window of silence that must pass in order for the
   *                observable to start echoing the last item
   */
  def echoRepeated(timeout: FiniteDuration): Observable[T] =
    operators.echo.apply(self, timeout, onlyOnce = false)

  /**
   * Hold an Observer's subscription request until the given `trigger`
   * observable either emits an item or completes, before passing it on to
   * the source Observable.
   *
   * If the given `trigger` completes in error, then the subscription is
   * terminated with `onError`.
   *
   * @param trigger - the observable that must either emit an item or
   *                complete in order for the source to be subscribed.
   */
  def delaySubscription[U](trigger: Observable[U]): Observable[T] =
    operators.delaySubscription.onTrigger(self, trigger)

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
   * Returns an Observable that emits the items emitted by the source
   * Observable shifted forward in time by a specified delay.
   *
   * Each time the source Observable emits an item, delay starts a timer,
   * and when that timer reaches the given duration, the Observable
   * returned from delay emits the same item.
   *
   * NOTE: this delay refers strictly to the time between the `onNext`
   * event coming from our source and the time it takes the downstream
   * observer to get this event. On the other hand the operator is also 
   * applying back-pressure, so on slow observers the actual time passing
   * between two successive events may be higher than the 
   * specified `duration`.
   *
   * @param duration - the delay to shift the source by
   * @return the source Observable shifted in time by the specified delay
   */
  def delay(duration: FiniteDuration): Observable[T] =
    operators.delay.byDuration(self, duration)

  /**
   * Returns an Observable that emits the items emitted by the source
   * Observable shifted forward in time.
   *
   * This variant of `delay` sets its delay duration on a per-item basis by
   * passing each item from the source Observable into a function that returns
   * an Observable and then monitoring those Observables. When any such
   * Observable emits an item or completes, the Observable returned
   * by delay emits the associated item.
   *
   * @see [[Observable!.delay(duration* delay(duration)]] for the other variant
   *
   * @param selector - a function that returns an Observable for each item
   *                   emitted by the source Observable, which is then used
   *                   to delay the emission of that item by the resulting
   *                   Observable until the Observable returned
   *                   from `selector` emits an item
   *
   * @return the source Observable shifted in time by
   *         the specified delay
   */
  def delay[U](selector: T => Observable[U]): Observable[T] =
    operators.delay.bySelector(self, selector)

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
   * Alias for [[Observable!.complete]].
   *
   * Ignores all items emitted by the source Observable and
   * only calls onCompleted or onError.
   *
   * @return an empty Observable that only calls onCompleted or onError,
   *         based on which one is called by the source Observable
   */
  def ignoreElements: Observable[Nothing] =
    operators.misc.complete(this)

  /**
   * Ignores all items emitted by the source Observable and
   * only calls onCompleted or onError.
   *
   * @return an empty Observable that only calls onCompleted or onError,
   *         based on which one is called by the source Observable
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
   *
   * @example {{{
   *   val source = 1 +: Observable(2, 3, 4)
   *   source.dump("O").subscribe()
   *
   *   // 0: O-->1
   *   // 1: O-->2
   *   // 2: O-->3
   *   // 3: O-->4
   *   // 4: O completed
   * }}}
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
   *
   * @example {{{
   *   val source = Observable(1, 2, 3) :+ 4
   *   source.dump("O").subscribe()
   *
   *   // 0: O-->1
   *   // 1: O-->2
   *   // 2: O-->3
   *   // 3: O-->4
   *   // 4: O completed
   * }}}
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
   *
   * Ordering of subscription is preserved, so the second observable
   * starts only after the source observable is completed successfully with
   * an `onComplete`. On the other hand, the second observable is never
   * subscribed if the source completes with an error.
   *
   * @example {{{
   *   val concat = Observable(1,2,3) ++ Observable(4,5)
   *   concat.dump("O").subscribe()
   *
   *   // 0: O-->1
   *   // 1: O-->2
   *   // 2: O-->3
   *   // 3: O-->4
   *   // 4: O-->5
   *   // 5: O completed
   * }}}
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
    operators.zip.two(self, other)

  /**
   * Zips the emitted elements of the source with their indices.
   */
  def zipWithIndex: Observable[(T, Long)] =
    operators.zip.withIndex(self)

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
   * This operator behaves in a similar way to [[zip]], but while `zip` emits items
   * only when all of the zipped source Observables have emitted a previously unzipped item,
   * `combine` emits an item whenever any of the source Observables emits
   * an item (so long as each of the source Observables has emitted at least one item).
   *
   * This version of [[Observable!.combineLatest combineLatest]]
   * is reserving `onError` notifications until all of the combined Observables
   * complete and only then passing it along to the observers.
   *
   * @see [[Observable!.combineLatest]]
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
    Observable.create(o => s.execute(onSubscribe(o)))
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
    operators.repeat.elements(self)

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers).
   */
  def multicast[U >: T, R](subject: Subject[U, R])(implicit s: Scheduler): ConnectableObservable[R] =
    ConnectableObservable(this, subject)

  /**
   * $asyncBoundaryDescription
   * 
   * @param overflowStrategy - $overflowStrategyParam
   */
  def asyncBoundary(overflowStrategy: OverflowStrategy): Observable[T] =
    Observable.create { subscriber =>
      onSubscribe(BufferedSubscriber(subscriber, overflowStrategy))
    }

  /**
   * $asyncBoundaryDescription
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @param onOverflow - $onOverflowParam
   */
  def asyncBoundary[U >: T](overflowStrategy: OverflowStrategy.Evicted, onOverflow: Long => U): Observable[U] =
    Observable.create { subscriber =>
      onSubscribe(BufferedSubscriber(subscriber, overflowStrategy))
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
   * @param onOverflow - $onOverflowParam
   */
  def whileBusyDropEvents[U >: T](onOverflow: Long => U): Observable[U] =
    operators.whileBusy.dropEventsThenSignalOverflow(self, onOverflow)

  /**
   * While the destination observer is busy, buffers events, applying
   * the given overflowStrategy.
   *
   * @param overflowStrategy - $overflowStrategyParam
   */
  def whileBusyBuffer[U >: T](overflowStrategy: OverflowStrategy.Synchronous): Observable[U] =
    asyncBoundary(overflowStrategy)

  /**
   * While the destination observer is busy, buffers events, applying
   * the given overflowStrategy.
   *
   * @param overflowStrategy - $overflowStrategyParam
   * @param onOverflow - $onOverflowParam
   */
  def whileBusyBuffer[U >: T](overflowStrategy: OverflowStrategy.Evicted, onOverflow: Long => U): Observable[U] =
    asyncBoundary(overflowStrategy, onOverflow)

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def publish()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(PublishSubject[T]())

  /**
   * Returns a new Observable that multi-casts (shares) the original Observable.
   */
  def share()(implicit s: Scheduler): Observable[T] =
    publish().refCount()

  /**
   * Caches the emissions from the source Observable and replays them
   * in order to any subsequent Subscribers. This method has similar
   * behavior to [[Observable!.replay()]] except that this auto-subscribes
   * to the source Observable rather than returning a [[ConnectableObservable]]
   * for which you must call [[ConnectableObservable.connect connect]]
   * to activate the subscription.
   *
   * When you call cache, it does not yet subscribe to the source Observable
   * and so does not yet begin caching items. This only happens when the
   * first Subscriber calls the resulting Observable's `subscribe` method.
   *
   * Note: You sacrifice the ability to cancel the origin when you use
   * the cache operator so be careful not to use this on Observables that emit an
   * infinite or very large number of items that will use up memory.
   *
   * @return an Observable that, when first subscribed to, caches all of its
   *         items and notifications for the benefit of subsequent subscribers
   */
  def cache: Observable[T] =
    CachedObservable.create(self)

  /**
   * Caches the emissions from the source Observable and replays them
   * in order to any subsequent Subscribers. This method has similar
   * behavior to [[Observable!.replay()]] except that this auto-subscribes
   * to the source Observable rather than returning a [[ConnectableObservable]]
   * for which you must call [[ConnectableObservable.connect connect]]
   * to activate the subscription.
   *
   * When you call cache, it does not yet subscribe to the source Observable
   * and so does not yet begin caching items. This only happens when the
   * first Subscriber calls the resulting Observable's `subscribe` method.
   *
   * @param maxCapacity is the maximum buffer size after which old events
   *                    start being dropped (according to what happens when using
   *                    [[ReplaySubject.createWithSize]]
   *
   * @return an Observable that, when first subscribed to, caches all of its
   *         items and notifications for the benefit of subsequent subscribers
   */
  def cache(maxCapacity: Int): Observable[T] =
    CachedObservable.create(self, maxCapacity)

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
   */
  def behavior[U >: T](initialValue: U)(implicit s: Scheduler): ConnectableObservable[U] =
    multicast(BehaviorSubject[U](initialValue))

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def replay()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(ReplaySubject[T]())

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   *
   * @param bufferSize is the size of the buffer limiting the number of items
   *                   that can be replayed (on overflow the head starts being
   *                   dropped)
   */
  def replay(bufferSize: Int)(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(ReplaySubject.createWithSize[T](bufferSize))

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def publishLast()(implicit s: Scheduler): ConnectableObservable[T] =
    multicast(AsyncSubject[T]())

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
   * applies a timeout overflowStrategy for each emitted item. If the next item
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
   * applies a timeout overflowStrategy for each emitted item. If the next item
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

    head.onSubscribe(new Observer[T] {
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
    onSubscribe(new SynchronousObserver[T] {
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
   * Observable constructor for creating an [[Observable]] from the
   * specified function.
   */
  def create[T](f: Subscriber[T] => Unit): Observable[T] = {
    new Observable[T] {
      def onSubscribe(subscriber: Subscriber[T]): Unit =
        try f(subscriber) catch {
          case NonFatal(ex) =>
            subscriber.onError(ex)
        }
    }
  }

  /**
   * Creates an observable that doesn't emit anything, but immediately
   * calls `onComplete` instead.
   */
  def empty: Observable[Nothing] =
    builders.unit.empty

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): Observable[A] =
    builders.unit.one(elem)

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): Observable[Nothing] =
    builders.unit.error(ex)

  /**
   * Creates an Observable that doesn't emit anything and that never
   * completes.
   */
  def never: Observable[Nothing] =
    builders.unit.never

  /**
   * Returns an Observable that calls an Observable factory to create
   * an Observable for each new Observer that subscribes. That is, for
   * each subscriber, the actual Observable that subscriber observes is
   * determined by the factory function.
   *
   * The defer Observer allows you to defer or delay emitting items
   * from an Observable until such time as an Observer subscribes
   * to the Observable. This allows an Observer to easily obtain updates
   * or a refreshed version of the sequence.
   *
   * @param factory is the Observable factory function to invoke for each
   *                Observer that subscribes to the resulting Observable
   */
  def defer[T](factory: => Observable[T]): Observable[T] = {
    create[T](s => factory.onSubscribe(s))
  }

  /**
   * Creates an Observable that emits auto-incremented natural numbers
   * (longs) spaced by a given time interval. Starts from 0 with no
   * delay, after which it emits incremented numbers spaced by the
   * `period` of time. The given `period` of time acts as a fixed
   * delay between successive events.
   *
   * @param delay the delay between 2 successive events
   */
  def intervalWithFixedDelay(delay: FiniteDuration): Observable[Long] =
    builders.interval.withFixedDelay(Duration.Zero, delay)

  /**
   * Creates an Observable that emits auto-incremented natural numbers
   * (longs) spaced by a given time interval. Starts from 0 with no
   * delay, after which it emits incremented numbers spaced by the
   * `period` of time. The given `period` of time acts as a fixed
   * delay between successive events.
   *
   * @param initialDelay is the delay to wait before emitting the first event
   * @param delay the time to wait between 2 successive events
   */
  def intervalWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration): Observable[Long] =
    builders.interval.withFixedDelay(initialDelay, delay)

  /**
   * Creates an Observable that emits auto-incremented natural numbers
   * (longs) spaced by a given time interval. Starts from 0 with no
   * delay, after which it emits incremented numbers spaced by the
   * `period` of time. The given `period` of time acts as a fixed
   * delay between successive events.
   *
   * @param delay the delay between 2 successive events
   */
  def interval(delay: FiniteDuration): Observable[Long] =
    intervalWithFixedDelay(delay)

  /**
   * Creates an Observable that emits auto-incremented natural numbers
   * (longs) at a fixed rate, as given by the specified `period`. The
   * time it takes to process an `onNext` event gets subtracted from
   * the specified `period` and thus the created observable tries to
   * emit events spaced by the given time interval, regardless of how
   * long the processing of `onNext` takes.
   *
   * @param period the period between 2 successive `onNext` events
   */
  def intervalAtFixedRate(period: FiniteDuration): Observable[Long] =
    builders.interval.atFixedRate(Duration.Zero, period)

  /**
   * Creates an Observable that emits auto-incremented natural numbers
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
    builders.interval.atFixedRate(initialDelay, period)


  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def repeat[T](elems: T*): Observable[T] =
    builders.repeat(elems : _*)

  /**
   * Repeats the execution of the given `task`, emitting
   * the results indefinitely.
   */
  def repeatTask[T](task: => T): Observable[T] =
    operators.repeat.task(task)

  /**
   * Creates an Observable that emits items in the given range.
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
   * Given an initial state and a generator function that produces
   * the next state and the next element in the sequence, creates
   * an observable that keeps generating elements produced by our
   * generator function.
   *
   * {{{
   *   from monifu.concurrent.Implicits.{globalScheduler => s}
   *   from monifu.util import Random
   *
   *   def randomDoubles(): Observable[Double] =
   *     Observable.fromStateAction(Random.double)(s.currentTimeMillis())
   * }}}
   */
  def fromStateAction[S,A](f: S => (A,S))(initialState: S): Observable[A] =
    builders.from.stateAction(f)(initialState)

  /**
   * Converts a Future to an Observable.
   */
  def fromFuture[T](future: Future[T]): Observable[T] =
    builders.from.future(future)

  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   */
  def fromIterable[T](iterable: Iterable[T]): Observable[T] =
    builders.from.iterable(iterable)

  /**
   * Creates an Observable that emits the elements of the given `iterator`.
   */
  def fromIterator[T](iterator: Iterator[T]): Observable[T] =
    builders.from.iterator(iterator)

  /**
   * Creates an Observable that emits the given elements exactly.
   */
  def from[T](elems: T*): Observable[T] =
    builders.from.iterable(elems)

  /**
   * Given a `org.reactivestreams.Publisher`, converts it into a
   * Monifu / Rx Observable.
   *
   * See the [[http://www.reactive-streams.org/ Reactive Streams]]
   * protocol that Monifu implements.
   *
   * @see [[Observable!.toReactive]] for converting ``
   */
  def fromReactivePublisher[T](publisher: RPublisher[T]): Observable[T] =
    Observable.create[T] { sub =>
      publisher.subscribe(sub.toReactive)
    }

  /**
   * Given a lazy by-name argument, converts it into an Observable
   * that emits a single element.
   */
  def fromTask[T](task: => T): Observable[T] =
    builders.from.task(task)

  /**
   * Given a runnable, converts it into an Observable that executes it,
   * then signals the execution with a `Unit` being emitted.
   */
  def fromRunnable(r: Runnable): Observable[Unit] =
    builders.from.runnable(r)

  /**
   * Given a `java.util.concurrent.Callable`, converts it into an
   * Observable that executes it, then emits the result.
   */
  def fromCallable[T](c: Callable[T]): Observable[T] =
    builders.from.callable(c)

  /**
   * Wraps this Observable into a `org.reactivestreams.Publisher`.
   * See the [[http://www.reactive-streams.org/ Reactive Streams]]
   * protocol that Monifu implements.
   */
  def toReactivePublisher[T](source: Observable[T])(implicit s: Scheduler): RPublisher[T] =
    new RPublisher[T] {
      def subscribe(subscriber: RSubscriber[_ >: T]): Unit = {
        source.onSubscribe(SafeSubscriber(Observer.fromReactiveSubscriber(subscriber)))
      }
    }

  /**
   * Wraps this Observable into a `org.reactivestreams.Publisher`.
   * See the [[http://www.reactive-streams.org/ Reactive Streams]]
   * protocol that Monifu implements.
   *
   * @param requestSize is
   */
  def toReactivePublisher[T](source: Observable[T], requestSize: Int)(implicit s: Scheduler): RPublisher[T] =
    new RPublisher[T] {
      def subscribe(subscriber: RSubscriber[_ >: T]): Unit = {
        source.onSubscribe(SafeSubscriber(Observer.fromReactiveSubscriber(subscriber)))
      }
    }

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
    Observable.fromIterable(sources).merge

  /**
   * Merges the given list of ''observables'' into a single observable.
   * Delays errors until the end.
   */
  def mergeDelayError[T](sources: Observable[T]*): Observable[T] =
    Observable.fromIterable(sources).mergeDelayErrors


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
   * Creates a new Observable from two observables, by emitting
   * elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are
   * ignored.
   */
  def zip[T1, T2](obs1: Observable[T1], obs2: Observable[T2]): Observable[(T1,T2)] =
    obs1.zip(obs2)

  /**
   * Creates a new Observable from three observables, by emitting
   * elements combined in tuples of 3 elements. If one of the
   * Observable emits fewer events than the others, then the rest of
   * the unpaired events are ignored.
   */
  def zip[T1, T2, T3](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3]): Observable[(T1, T2, T3)] =
    obs1.zip(obs2).zip(obs3).map { case ((t1, t2), t3) => (t1, t2, t3) }

  /**
   * Creates a new Observable from three observables, by emitting
   * elements combined in tuples of 4 elements. If one of the
   * Observable emits fewer events than the others, then the rest of
   * the unpaired events are ignored.
   */
  def zip[T1, T2, T3, T4](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3], obs4: Observable[T4]): Observable[(T1, T2, T3, T4)] =
    obs1.zip(obs2).zip(obs3).zip(obs4).map { case (((t1, t2), t3), t4) => (t1, t2, t3, t4) }

  /**
   * Creates a new Observable from three observables, by emitting
   * elements combined in tuples of 5 elements. If one of the
   * Observable emits fewer events than the others, then the rest of
   * the unpaired events are ignored.
   */
  def zip[T1, T2, T3, T4, T5](
    obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3],
    obs4: Observable[T4], obs5: Observable[T5]): Observable[(T1, T2, T3, T4, T5)] = {

    obs1.zip(obs2).zip(obs3).zip(obs4).zip(obs5)
      .map { case ((((t1, t2), t3), t4), t5) => (t1, t2, t3, t4, t5) }
  }

  /**
   * Creates a new Observable from three observables, by emitting
   * elements combined in tuples of 6 elements. If one of the
   * Observable emits fewer events than the others, then the rest of
   * the unpaired events are ignored.
   */
  def zip[T1, T2, T3, T4, T5, T6](
    obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3],
    obs4: Observable[T4], obs5: Observable[T5], obs6: Observable[T6]): Observable[(T1, T2, T3, T4, T5, T6)] = {

    obs1.zip(obs2).zip(obs3).zip(obs4).zip(obs5).zip(obs6)
      .map { case (((((t1, t2), t3), t4), t5), t6) => (t1, t2, t3, t4, t5, t6) }
  }

  /**
   * Given an observable sequence, it [[Observable!.zip zips]] them together
   * returning a new observable that generates sequences.
   */
  def zipList[T](sources: Observable[T]*): Observable[Seq[T]] = {
    if (sources.isEmpty) Observable.empty else {
      val seed = sources.head.map(t => Vector(t))
      sources.tail.foldLeft(seed) { (acc, obs) =>
        acc.zip(obs).map { case (seq, elem) => seq :+ elem }
      }
    }
  }

  /**
   * Creates a combined observable from 2 source observables.
   *
   * This operator behaves in a similar way to [[Observable!.zip]],
   * but while `zip` emits items only when all of the zipped source
   * Observables have emitted a previously unzipped item, `combine`
   * emits an item whenever any of the source Observables emits an
   * item (so long as each of the source Observables has emitted at
   * least one item).
   */
  def combineLatest[T1, T2](first: Observable[T1], second: Observable[T2]): Observable[(T1,T2)] = {
    first.combineLatest(second)
  }

  /**
   * Creates a combined observable from 3 source observables.
   *
   * This operator behaves in a similar way to [[Observable!.zip]],
   * but while `zip` emits items only when all of the zipped source
   * Observables have emitted a previously unzipped item, `combine`
   * emits an item whenever any of the source Observables emits an
   * item (so long as each of the source Observables has emitted at
   * least one item).
   */
  def combineLatest[T1, T2, T3]
      (first: Observable[T1], second: Observable[T2], third: Observable[T3]): Observable[(T1,T2,T3)] = {

    first.combineLatest(second).combineLatest(third)
      .map { case ((t1, t2), t3) => (t1, t2, t3) }
  }

  /**
   * Creates a combined observable from 4 source observables.
   *
   * This operator behaves in a similar way to [[Observable!.zip]],
   * but while `zip` emits items only when all of the zipped source
   * Observables have emitted a previously unzipped item, `combine`
   * emits an item whenever any of the source Observables emits an
   * item (so long as each of the source Observables has emitted at
   * least one item).
   */
  def combineLatest[T1, T2, T3, T4]
      (first: Observable[T1], second: Observable[T2],
       third: Observable[T3], fourth: Observable[T4]): Observable[(T1, T2, T3, T4)] = {

    first.combineLatest(second).combineLatest(third).combineLatest(fourth)
      .map { case (((t1, t2), t3), t4) => (t1, t2, t3, t4) }
  }

  /**
   * Creates a combined observable from 5 source observables.
   *
   * This operator behaves in a similar way to [[Observable!.zip]],
   * but while `zip` emits items only when all of the zipped source
   * Observables have emitted a previously unzipped item, `combine`
   * emits an item whenever any of the source Observables emits an
   * item (so long as each of the source Observables has emitted at
   * least one item).
   */
  def combineLatest[T1, T2, T3, T4, T5](
    obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3],
    obs4: Observable[T4], obs5: Observable[T5]): Observable[(T1, T2, T3, T4, T5)] = {

    obs1.combineLatest(obs2).combineLatest(obs3)
      .combineLatest(obs4).combineLatest(obs5)
      .map { case ((((t1, t2), t3), t4), t5) => (t1, t2, t3, t4, t5) }
  }

  /**
   * Creates a combined observable from 6 source observables.
   *
   * This operator behaves in a similar way to [[Observable!.zip]],
   * but while `zip` emits items only when all of the zipped source
   * Observables have emitted a previously unzipped item, `combine`
   * emits an item whenever any of the source Observables emits an
   * item (so long as each of the source Observables has emitted at
   * least one item).
   */
  def combineLatest[T1, T2, T3, T4, T5, T6](
    obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3],
    obs4: Observable[T4], obs5: Observable[T5], obs6: Observable[T6]): Observable[(T1, T2, T3, T4, T5, T6)] = {

    obs1.combineLatest(obs2).combineLatest(obs3)
      .combineLatest(obs4).combineLatest(obs5).combineLatest(obs6)
      .map { case (((((t1, t2), t3), t4), t5), t6) => (t1, t2, t3, t4, t5, t6) }
  }

  /**
   * Given an observable sequence, it [[Observable!.zip zips]] them together
   * returning a new observable that generates sequences.
   */
  def combineLatestList[T](sources: Observable[T]*): Observable[Seq[T]] = {
    if (sources.isEmpty) Observable.empty else {
      val seed = sources.head.map(t => Vector(t))
      sources.tail.foldLeft(seed) { (acc, obs) =>
        acc.combineLatest(obs).map { case (seq, elem) => seq :+ elem }
      }
    }
  }

  /**
   * Given a list of source Observables, emits all of the items from
   * the first of these Observables to emit an item and cancel the
   * rest.
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
  implicit def ObservableIsReactive[T](source: Observable[T])(implicit s: Scheduler): RPublisher[T] =
    source.toReactive
}
