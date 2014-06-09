package monifu.reactive

import language.implicitConversions
import monifu.concurrent.Scheduler
import scala.concurrent.{Promise, Future}
import monifu.reactive.api._
import Ack.{Cancel, Continue}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.util.{Failure, Success}
import monifu.reactive.observers.SafeObserver
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.observables.{ConnectableObservable, GenericObservable}
import monifu.concurrent.atomic.Atomic
import monifu.reactive.api.BufferPolicy.BackPressured

/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] {
  /**
   * Characteristic function for an `Observable` instance,
   * that creates the subscription and that starts the stream,
   * being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  protected def subscribeFn(observer: Observer[T]): Unit

  /**
   * Implicit scheduler required for asynchronous boundaries.
   */
  implicit def scheduler: Scheduler

  /**
   * Creates the subscription and that starts the stream.
   *
   * This function is "unsafe" to call because it does not wrap the given
   * observer implementation in a [[monifu.reactive.observers.SafeObserver SafeObserver]],
   * like the other subscribe functions are doing.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] that respects
   *                 Monifu Rx contract.
   */
  final def unsafeSubscribe(observer: Observer[T]): Unit = {
    subscribeFn(observer)
  }

  /**
   * Creates the subscription and that starts the stream.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  final def subscribe(observer: Observer[T]): Unit = {
    unsafeSubscribe(SafeObserver[T](observer))
  }

  /**
   * Creates the subscription and starts the stream.
   */
  final def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit, completedFn: () => Unit): Unit =
    subscribe(new Observer[T] {
      def onNext(elem: T) = nextFn(elem)
      def onError(ex: Throwable) = errorFn(ex)
      def onComplete() = completedFn()
    })

  /**
   * Creates the subscription and starts the stream.
   */
  final def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit): Unit =
    subscribe(nextFn, errorFn, () => Cancel)

  /**
   * Creates the subscription and starts the stream.
   */
  final def subscribe(): Unit =
    subscribe(elem => Continue)

  /**
   * Creates the subscription and starts the stream.
   */
  final def subscribe(nextFn: T => Future[Ack]): Unit =
    subscribe(nextFn, error => { scheduler.reportFailure(error); Cancel }, () => Cancel)

  /**
   * Returns an Observable that applies the given function to each item emitted by an
   * Observable and emits the result.
   *
   * @param f a function to apply to each item emitted by the Observable
   * @return an Observable that emits the items from the source Observable, transformed by the given function
   */
  def map[U](f: T => U): Observable[U]

  /**
   * Returns an Observable which only emits those items for which the given predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only those items in the original Observable for which the filter evaluates as `true`
   */
  def filter(p: T => Boolean): Observable[T]

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
  def flatMap[U](f: T => Observable[U]): Observable[U]

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
  def concatMap[U](f: T => Observable[U]): Observable[U]

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
  def mergeMap[U](f: T => Observable[U]): Observable[U]

  /**
   * Flattens the sequence of Observables emitted by the source into one Observable, without any
   * transformation.
   *
   * You can combine the items emitted by multiple Observables so that they act like a single
   * Observable by using this method.
   *
   * This operation is only available if `this` is of type `Observable[Observable[B]]` for some `B`,
   * otherwise you'll get a compilation error.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def flatten[U](implicit ev: T <:< Observable[U]): Observable[U]

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
  def concat[U](implicit ev: T <:< Observable[U]): Observable[U]

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
   *                     available [[monifu.reactive.api.BufferPolicy buffer policies]].
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](bufferPolicy: BufferPolicy)(implicit ev: T <:< Observable[U]): Observable[U]

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
   *                     available [[monifu.reactive.api.BufferPolicy buffer policies]].
   *
   * @param parallelism a number indicating the maximum number of observables subscribed
   *                    in parallel; if negative or zero, then no upper bound is applied
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](parallelism: Int, bufferPolicy: BufferPolicy)(implicit ev: T <:< Observable[U]): Observable[U]

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
   * This variant of the merge call (no parameters) does apply
   * [[api.BufferPolicy.BackPressured back-pressured buffering]] and also applies an upper-bound
   * on the number of observables subscribed, so it is fairly safe to use.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](implicit ev: T <:< Observable[U]): Observable[U]

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
   * This unsafe variant of the merge call applies absolutely no back-pressure or upper bounds
   * on subscribed observables, so it is unsafe to use. Only use it when you know what
   * you're doing.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def unsafeMerge[U](implicit ev: T <:< Observable[U]): Observable[U]

  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  def take(n: Int): Observable[T]

  def takeRight(n: Int): Observable[T]

  /**
   * Drops the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to drop
   *  @return    a new Observable that drops the first ''n'' elements
   *             emitted by the source
   */
  def drop(n: Int): Observable[T]

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(p: T => Boolean): Observable[T]

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(isRefTrue: Atomic[Boolean]): Observable[T]

  /**
   * Drops the longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits the rest.
   */
  def dropWhile(p: T => Boolean): Observable[T]

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R]

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def reduce[U >: T](op: (U, U) => U): Observable[U]

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits on each step the result
   * of the applied function.
   *
   * Similar to [[foldLeft]], but emits the state on each step. Useful for modeling finite
   * state machines.
   */
  def scan[R](initial: R)(op: (R, T) => R): Observable[R]

  /**
   * Given a start value (a seed) and a function taking the current state
   * (starting with the seed) and the currently emitted item and returning a new
   * state value as a `Future`, it returns a new Observable that applies the given
   * function to all emitted items, emitting the produced state along the way.
   *
   * This operator is to [[scan]] what [[flatMap]] is to [[map]].
   *
   * Example: {{{
   *   // dumb long running function, returning a Future result
   *   def sumUp(x: Long, y: Int) = Future(x + y)
   *
   *   Observable.range(0, 10).flatScan(0L)(sumUp).dump("FlatScan").subscribe()
   *   //=> 0: FlatScan-->0
   *   //=> 1: FlatScan-->1
   *   //=> 2: FlatScan-->3
   *   //=> 3: FlatScan-->6
   *   //=> 4: FlatScan-->10
   *   //=> 5: FlatScan-->15
   *   //=> 6: FlatScan-->21
   *   //=> 7: FlatScan-->28
   *   //=> 8: FlatScan-->36
   *   //=> 9: FlatScan-->45
   *   //=> 10: FlatScan completed
   * }}}
   *
   * NOTE that it does back-pressure and the state produced by this function is
   * emitted in order of the original input. This is the equivalent of
   * [[concatMap]] and NOT [[mergeMap]] (a mergeScan wouldn't make sense anyway).
   */
  def flatScan[R](initial: R)(op: (R, T) => Future[R]): Observable[R]

  /**
   * Applies a binary operator to the first item emitted by a source Observable,
   * then feeds the result of that function along with the second item emitted by
   * the source Observable into the same function, and so on until all items have been
   * emitted by the source Observable, emitting the result of each of these iterations.
   *
   * Similar to [[reduce]], but emits the state on each step. Useful for modeling finite
   * state machines.
   */
  def scan[U >: T](op: (U, U) => U): Observable[U]

  /**
   * Executes the given callback when the stream has ended on `onComplete`
   * (after the event was already emitted)
   *
   * NOTE: protect the callback such that it doesn't throw exceptions, because
   * it gets executed after `onComplete()` happens and by definition the error cannot
   * be streamed with `onError()`.
   *
   * @param cb the callback to execute when the subscription is canceled
   */
  def doOnComplete(cb: => Unit): Observable[T]

  /**
   * Executes the given callback for each element generated by the source
   * Observable, useful for doing side-effects.
   *
   * @return a new Observable that executes the specified callback for each element
   */
  def doWork(cb: T => Unit): Observable[T]

  /**
   * Executes the given callback only for the first element generated by the source
   * Observable, useful for doing a piece of computation only when the stream started.
   *
   * @return a new Observable that executes the specified callback only for the first element
   */
  def doOnStart(cb: T => Unit): Observable[T]

  /**
   * Returns an Observable which only emits the first item for which the predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only the first item in the original Observable for which the filter evaluates as `true`
   */
  def find(p: T => Boolean): Observable[T]

  /**
   * Returns an Observable which emits a single value, either true, in case the given predicate holds for at least
   * one item, or false otherwise.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for at least one item
   */
  def exists(p: T => Boolean): Observable[Boolean]

  /**
   * Returns an Observable that emits a single boolean, either true, in case the given predicate holds for all the items
   * emitted by the source, or false in case at least one item is not verifying the given predicate.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for all the items
   */
  def forAll(p: T => Boolean): Observable[Boolean]

  /**
   * Returns an Observable that doesn't emit anything, but that completes when the source Observable completes.
   */
  def complete: Observable[Nothing]

  /**
   * Returns an Observable that emits a single Throwable, in case an error was thrown by the source Observable,
   * otherwise it isn't going to emit anything.
   */
  def error: Observable[Throwable]

  /**
   * Emits the given exception instead of `onComplete`.
   * @param error the exception to emit onComplete
   * @return a new Observable that emits an exception onComplete
   */
  def endWithError(error: Throwable): Observable[T]

  /**
   * Creates a new Observable that emits the given elements
   * and then it also emits the events of the source (prepend operation).
   */
  def +:[U >: T](elems: U*): Observable[U]

  /**
   * Creates a new Observable that emits the given elements
   * and then it also emits the events of the source (prepend operation).
   */
  def startWith[U >: T](elems: U*): Observable[U]

  /**
   * Creates a new Observable that emits the events of the source
   * and then it also emits the given elements (appended to the stream).
   */
  def :+[U >: T](elems: U*): Observable[U]

  /**
   * Creates a new Observable that emits the events of the source
   * and then it also emits the given elements (appended to the stream).
   */
  def endWith[U >: T](elems: U*): Observable[U]

  /**
   * Concatenates the source Observable with the other Observable, as specified.
   */
  def ++[U >: T](other: => Observable[U]): Observable[U]

  /**
   * Only emits the first element emitted by the source observable, after which it's completed immediately.
   */
  def head: Observable[T]

  /**
   * Drops the first element of the source observable, emitting the rest.
   */
  def tail: Observable[T]

  /**
   * Only emits the last element emitted by the source observable, after which it's completed immediately.
   */
  def last: Observable[T]

  /**
   * Emits the first element emitted by the source, or otherwise if the source is completed without
   * emitting anything, then the `default` is emitted.
   */
  def headOrElse[B >: T](default: => B): Observable[B]

  /**
   * Emits the first element emitted by the source, or otherwise if the source is completed without
   * emitting anything, then the `default` is emitted.
   *
   * Alias for `headOrElse`.
   */
  def firstOrElse[U >: T](default: => U): Observable[U]

  /**
   * Creates a new Observable from this Observable and another given Observable,
   * by emitting elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are ignored.
   */
  def zip[U](other: Observable[U]): Observable[(T, U)]

  /**
   * Takes the elements of the source Observable and emits the maximum value,
   * after the source has completed.
   */
  def max[U >: T](implicit ev: Ordering[U]): Observable[U]

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the maximum key value, where the key is generated by the given function `f`.
   */
  def maxBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T]

  /**
   * Takes the elements of the source Observable and emits the minimum value,
   * after the source has completed.
   */
  def min[U >: T](implicit ev: Ordering[U]): Observable[T]

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the minimum key value, where the key is generated by the given function `f`.
   */
  def minBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T]

  /**
   * Given a source that emits numeric values, the `sum` operator
   * sums up all values and at onComplete it emits the total.
   */
  def sum[U >: T](implicit ev: Numeric[U]): Observable[U]

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for listening to the emitted items.
   */
  def observeOn(s: Scheduler): Observable[T]

  /**
   * Suppress the duplicate elements emitted by the source Observable.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct: Observable[T]

  /**
   * Given a function that returns a key for each element emitted by
   * the source Observable, suppress duplicates items.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct[U](fn: T => U): Observable[T]

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged: Observable[T]

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged[U](fn: T => U): Observable[T]

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for initiating the subscription.
   */
  def subscribeOn(s: Scheduler): Observable[T]

  /**
   * Converts the source Observable that emits `T` into an Observable
   * that emits `Notification[T]`.
   *
   * NOTE: `onComplete` is still emitted after an `onNext(OnComplete)` notification
   * however an `onError(ex)` notification is emitted as an `onNext(OnError(ex))`
   * followed by an `onComplete`.
   */
  def materialize: Observable[Notification[T]]

  /**
   * Utility that can be used for debugging purposes.
   */
  def dump(prefix: String): Observable[T]

  /**
   * Repeats the items emitted by this Observable continuously. It caches the generated items until `onComplete`
   * and repeats them ad infinitum. On error it terminates.
   */
  def repeat: Observable[T]

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers).
   */
  def multicast[R](subject: Subject[T, R]): ConnectableObservable[R]

  /**
   * Wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.SafeObserver SafeObserver]].
   * Normally wrapping in a `SafeObserver` happens at the edges of the monad
   * (in the user-facing `subscribe()` implementation) or in Observable subscribe implementations,
   * so this wrapping is useful.
   */
  def safe: Observable[T]

  /**
   * Wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.ConcurrentObserver ConcurrentObserver]].
   *
   * Normally Monifu's implementation guarantees that events are not emitted concurrently,
   * and that the publisher MUST NOT emit the next event without acknowledgement from the consumer
   * that it may proceed, however for badly behaved publishers, this wrapper provides
   * the guarantee that the downstream [[monifu.reactive.Observer Observer]] given in `subscribe` will not receive
   * concurrent events, also making it thread-safe.
   *
   * WARNING: the buffer created by this operator is unbounded and can blow up the process if the
   * data source is pushing events without following the back-pressure requirements and faster than
   * what the destination consumer can consume. On the other hand, if the data-source does follow
   * the back-pressure contract, than this is safe. For data sources that cannot respect the
   * back-pressure requirements and are problematic, see [[buffered]] and
   * [[monifu.reactive.api.BufferPolicy BufferPolicy]] for options.
   */
  def concurrent: Observable[T]

  /**
   * Wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.BufferedObserver BufferedObserver]].
   *
   * Normally Monifu's implementation guarantees that events are not emitted concurrently,
   * and that the publisher MUST NOT emit the next event without acknowledgement from the consumer
   * that it may proceed, however for badly behaved publishers, this wrapper provides
   * the guarantee that the downstream [[monifu.reactive.Observer Observer]] given in `subscribe` will not receive
   * concurrent events.
   *
   * Compared with [[concurrent]] / [[monifu.reactive.observers.ConcurrentObserver ConcurrentObserver]], the acknowledgement
   * given by [[monifu.reactive.observers.BufferedObserver BufferedObserver]] is synchronous
   * (i.e. the `Future[Ack]` is already completed), so the publisher can send the next event without waiting for
   * the consumer to receive and process the previous event (i.e. the data source will receive the `Continue`
   * acknowledgement once the event has been buffered, not when it has been received by its destination).
   *
   * WARNING: if the buffer created by this operator is unbounded, it can blow up the process if the data source
   * is pushing events faster than what the observer can consume, as it introduces an asynchronous
   * boundary that eliminates the back-pressure requirements of the data source. Unbounded is the default
   * [[monifu.reactive.api.BufferPolicy policy]], see [[monifu.reactive.api.BufferPolicy BufferPolicy]]
   * for options.
   */
  def buffered(policy: BufferPolicy = BackPressured(bufferSize = 4096)): Observable[T]

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def publish(): ConnectableObservable[T]

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
   */
  def behavior[U >: T](initialValue: U): ConnectableObservable[U]

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def replay(): ConnectableObservable[T]

  /**
   * Given a function that transforms an `Observable[T]` into an `Observable[U]`,
   * it transforms the source observable into an `Observable[U]`.
   */
  def lift[U](f: Observable[T] => Observable[U]): Observable[U]

  /**
   * Returns the first generated result as a Future and then cancels
   * the subscription.
   */
  final def asFuture: Future[Option[T]] = {
    val promise = Promise[Option[T]]()

    head.unsafeSubscribe(new Observer[T] {
      def onNext(elem: T) = {
        promise.trySuccess(Some(elem))
        Cancel
      }

      def onComplete() = {
        promise.trySuccess(None)
        Cancel
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
        Cancel
      }
    })

    promise.future
  }

  /**
   * Subscribes to the source `Observable` and foreach element emitted by the source
   * it executes the given callback.
   */
  final def foreach(cb: T => Unit): Unit =
    unsafeSubscribe(new Observer[T] {
      def onNext(elem: T) =
        try { cb(elem); Continue } catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }

      def onComplete() = Cancel
      def onError(ex: Throwable) = {
        scheduler.reportFailure(ex)
        Cancel
      }
    })
}

object Observable {
  /**
   * Observable constructor for creating an [[Observable]] from the specified function.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/create.png" />
   *
   * Example: {{{
   *   import monifu.reactive._
   *   import monifu.reactive.api.Ack.Continue
   *   import monifu.concurrent.Scheduler
   *
   *   def emit[T](elem: T, nrOfTimes: Int)(implicit scheduler: Scheduler): Observable[T] =
   *     Observable.create { observer =>
   *       def loop(times: Int): Unit =
   *         scheduler.scheduleOnce {
   *           if (times > 0)
   *             observer.onNext(elem).onSuccess {
   *               case Continue => loop(times - 1)
   *             }
   *           else
   *             observer.onComplete()
   *         }
   *       loop(nrOfTimes)
   *     }
   *
   *   // usage sample
   *   import monifu.concurrent.Scheduler.Implicits.global

   *   emit(elem=30, nrOfTimes=3).dump("Emit").subscribe()
   *   //=> 0: Emit-->30
   *   //=> 1: Emit-->30
   *   //=> 2: Emit-->30
   *   //=> 3: Emit completed
   * }}}
   */
  def create[T](f: Observer[T] => Unit)(implicit scheduler: Scheduler): Observable[T] =
    GenericObservable.create(f)

  /**
   * Creates an observable that doesn't emit anything, but immediately calls `onComplete`
   * instead.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/empty.png" />
   */
  def empty[A](implicit scheduler: Scheduler): Observable[A] =
    Observable.create { observer =>
      SafeObserver(observer).onComplete()
    }

  /**
   * Creates an Observable that only emits the given ''a''
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/unit.png" />
   */
  def unit[A](elem: A)(implicit scheduler: Scheduler): Observable[A] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      observer.onNext(elem).onSuccess {
        case Continue =>
          observer.onComplete()
      }
    }
  }

  /**
   * Creates an Observable that emits an error.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/error.png" />
   */
  def error(ex: Throwable)(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { observer =>
      SafeObserver[Nothing](observer).onError(ex)
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/never.png"" />
   */
  def never(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { _ => () }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/interval.png"" />
   *
   * @param period the delay between two subsequent events
   * @param scheduler the execution context in which `onNext` will get called
   */
  def interval(period: FiniteDuration)(implicit scheduler: Scheduler): Observable[Long] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      var counter = 0

      scheduler.scheduleRecursive(Duration.Zero, period, { reschedule =>
        val result = observer.onNext(counter)
        counter += 1

        result.onSuccess {
          case Continue =>
            reschedule()
        }
      })
    }
  }

  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def repeat[T](elems: T*)(implicit scheduler: Scheduler): Observable[T] = {
    if (elems.size == 0) Observable.empty
    else if (elems.size == 1) {
      Observable.create { o =>
        val observer = SafeObserver(o)

        def loop(elem: T): Unit =
          scheduler.execute(new Runnable {
            def run(): Unit =
              observer.onNext(elem).onSuccess {
                case Continue =>
                  loop(elem)
              }
          })

        loop(elems.head)
      }
    }
    else
      Observable.from(elems).repeat
  }

  /**
   * Creates an Observable that emits items in the given range.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/range.png" />
   *
   * @param from the range start
   * @param until the range end
   * @param step increment step, either positive or negative
   */
  def range(from: Int, until: Int, step: Int = 1)(implicit scheduler: Scheduler): Observable[Int] = {
    require(step != 0, "step must be a number different from zero")

    Observable.create { o =>
      val observer = SafeObserver(o)

      def scheduleLoop(from: Int, until: Int, step: Int): Unit =
        scheduler.execute(new Runnable {
          @tailrec
          def loop(from: Int, until: Int, step: Int): Unit =
            if ((step > 0 && from < until) || (step < 0 && from > until)) {
              observer.onNext(from) match {
                case Continue =>
                  loop(from + step, until, step)
                case Cancel =>
                  // do nothing else
                case async =>
                  async.onSuccess {
                    case Continue =>
                      scheduleLoop(from + step, until, step)
                  }
              }
            }
            else
              observer.onComplete()

          def run(): Unit =
            loop(from, until, step)
        })

      scheduleLoop(from, until, step)
    }
  }

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
  def apply[T](elems: T*)(implicit scheduler: Scheduler): Observable[T] = {
    from(elems)
  }

  /**
   * Converts a Future to an Observable.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](future: Future[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      future.onComplete {
        case Success(value) =>
          observer.onNext(value)
            .onContinueTriggerComplete(observer)
        case Failure(ex) =>
          observer.onError(ex)
      }
    }

  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](iterable: Iterable[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      def startFeedLoop(iterator: Iterator[T]): Unit =
        scheduler.execute(new Runnable {
          def run(): Unit =
            while (true) {
              try {
                if (iterator.hasNext) {
                  val elem = iterator.next()

                  observer.onNext(elem) match {
                    case Continue =>
                    // continue loop
                    case Cancel =>
                      return
                    case async =>
                      async.onSuccess {
                        case Continue =>
                          startFeedLoop(iterator)
                      }
                      return // interrupt the loop
                  }
                }
                else {
                  observer.onComplete()
                  return
                }
              }
              catch {
                case NonFatal(ex) =>
                  observer.onError(ex)
              }
            }
        })

      val iterator = iterable.iterator
      startFeedLoop(iterator)
    }

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).flatten

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def merge[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).merge

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
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def concat[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).concat

  /**
   * Implicit conversion from Future to Observable.
   */
  implicit def FutureIsObservable[T](future: Future[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(future)
}
