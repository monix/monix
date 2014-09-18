/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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

import monifu.concurrent.atomic.{Atomic, AtomicBoolean, AtomicInt}
import monifu.concurrent.cancelables.{SingleAssignmentCancelable, BooleanCancelable, RefCountCancelable}
import monifu.concurrent.locks.SpinLock
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering, Unbounded}
import monifu.reactive.Notification.{OnComplete, OnError, OnNext}
import monifu.reactive.internals._
import monifu.reactive.observers._
import monifu.reactive.subjects.{AsyncSubject, BehaviorSubject, PublishSubject, ReplaySubject}
import org.reactivestreams.{Publisher, Subscriber}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import BufferPolicy.{default => defaultPolicy}

/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] { self =>
  /**
   * Characteristic function for an `Observable` instance,
   * that creates the subscription and that eventually starts the streaming of events
   * to the given [[Observer]], being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  protected def subscribeFn(observer: Observer[T]): Unit

  /**
   * Implicit execution context required for asynchronous boundaries.
   */
  implicit def context: ExecutionContext

  /**
   * Creates the subscription and that starts the stream.
   *
   * @param observer is an [[monifu.reactive.Observer Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  def subscribe(observer: Observer[T]): Cancelable = {
    val isRunning = Atomic(true)
    takeWhile(isRunning).unsafeSubscribe(SafeObserver[T](observer))
    Cancelable { isRunning := false }
  }

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
  def unsafeSubscribe(observer: Observer[T]): Unit = {
    subscribeFn(observer)
  }

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(new Observer[T] {
      def onNext(elem: T) = nextFn(elem)
      def onComplete() = completedFn()
      def onError(ex: Throwable) = errorFn(ex)
    })

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack], errorFn: Throwable => Unit): Cancelable =
    subscribe(nextFn, errorFn, () => Cancel)

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(): Cancelable =
    subscribe(elem => Continue)

  /**
   * Creates the subscription and starts the stream.
   */
  def subscribe(nextFn: T => Future[Ack]): Cancelable =
    subscribe(nextFn, error => { context.reportFailure(error); Cancel }, () => Cancel)

  /**
   * Wraps this Observable into a `org.reactivestreams.Publisher`.
   */
  def publisher[U >: T]: Publisher[U] =
    new Publisher[U] {
      def subscribe(s: Subscriber[_ >: U]): Unit = {
        subscribeFn(SafeObserver(Observer.from(s)))
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
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            val next = f(elem)
            streamError = false
            observer.onNext(next)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete() =
          observer.onComplete()
      })
    }

  /**
   * Returns an Observable which only emits those items for which the given predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only those items in the original Observable for which the filter evaluates as `true`
   */
  def filter(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            if (p(elem)) {
              streamError = false
              observer.onNext(elem)
            }
            else
              Continue
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete() =
          observer.onComplete()
      })
    }

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
   * @param f a function that, when applied to an item emitted by the source Observable, returns an Observable
   * @return an Observable that emits the result of applying the transformation function to each
   *         item emitted by the source Observable and concatenating the results of the Observables
   *         obtained from this transformation.
   */
  def concatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).concat

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
  def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] = concat

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
    Observable.create { observerU =>
      unsafeSubscribe(new Observer[T] {
        private[this] val refCount = RefCountCancelable(observerU.onComplete())

        def onNext(childObservable: T) = {
          val upstreamPromise = Promise[Ack]()
          val refID = refCount.acquire()

          childObservable.unsafeSubscribe(new Observer[U] {
            def onNext(elem: U) = {
              observerU.onNext(elem)
                .ifCancelTryCanceling(upstreamPromise)
            }

            def onError(ex: Throwable) = {
              // error happened, so signaling both the main thread that it should stop
              // and the downstream consumer of the error
              observerU.onError(ex)
              upstreamPromise.trySuccess(Cancel)
            }

            def onComplete() = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // instead we are just instructing upstream to send the next observable
              if (upstreamPromise.trySuccess(Continue)) {
                refID.cancel()
              }
            }
          })

          upstreamPromise.future
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          observerU.onError(ex)
        }

        def onComplete() = {
          refCount.cancel()
        }
      })
    }

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
   * @param batchSize a number indicating the maximum number of observables subscribed
   *                  in parallel; if negative or zero, then no upper bound is applied
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](bufferPolicy: BufferPolicy = defaultPolicy, batchSize: Int = 0)(implicit ev: T <:< Observable[U]): Observable[U] =
    Observable.create { observerB =>

      // if the parallelism is unbounded and the buffer policy allows for a
      // synchronous buffer, then we can use a more efficient implementation
      bufferPolicy match {
        case Unbounded | OverflowTriggering(_) if batchSize <= 0 =>
          unsafeSubscribe(new SynchronousObserver[T] {
            private[this] val buffer =
              new UnboundedMergeBuffer[U](observerB, bufferPolicy)
            def onNext(elem: T): Ack =
              buffer.merge(elem)
            def onError(ex: Throwable): Unit =
              buffer.onError(ex)
            def onComplete(): Unit =
              buffer.onComplete()
          })

        case _ =>
          unsafeSubscribe(new Observer[T] {
            private[this] val buffer: BoundedMergeBuffer[U] =
              new BoundedMergeBuffer[U](observerB, batchSize, bufferPolicy)
            def onNext(elem: T) =
              buffer.merge(elem)
            def onError(ex: Throwable) =
              buffer.onError(ex)
            def onComplete() =
              buffer.onComplete()
          })
      }
    }

  /**
   * Given the source observable and another `Observable`, emits all of the items
   * from the first of these Observables to emit an item and cancel the other.
   */
  def ambWith[U >: T](other: Observable[U]): Observable[U] = {
    Observable.amb(this, other)
  }

  /**
   * Emit items from the source Observable, or emit a default item if
   * the source Observable completes after emitting no items.
   */
  def defaultIfEmpty[U >: T](default: U): Observable[U] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] var isEmpty = true

        def onNext(elem: T): Future[Ack] = {
          if (isEmpty) isEmpty = false
          observer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (isEmpty) observer.onNext(default)
          observer.onComplete()
        }
      })
    }

  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  def take(n: Int): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var counter = 0
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (n <= 0 && !isDone) {
            isDone = true
            observer.onComplete()
            Cancel
          }
          else if (!isDone && counter < n) {
            // ^^ short-circuit for not endlessly incrementing that number
            counter += 1

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              observer.onNext(elem)
            }
            else  {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              isDone = true
              observer.onNext(elem)
              observer.onComplete()
              Cancel
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            Cancel
          }
        }

        def onError(ex: Throwable) =
          if (!isDone) {
            isDone = true
            observer.onError(ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            observer.onComplete()
          }
      })
    }

  /**
   * Creates a new Observable that emits the events of the source, only
   * for the specified `timestamp`, after which it completes.
   *
   * @param timespan the window of time during which the new Observable
   *                 is allowed to emit the events of the source
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the timeout event.
   */
  def take(timespan: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] val lock = SpinLock()
        private[this] var isDone = false

        private[this] val task =
          scheduler.scheduleOnce(timespan, onComplete())

        def onNext(elem: T): Future[Ack] =
          lock.enter {
            if (!isDone)
              observer.onNext(elem).onCancel {
                lock.enter {
                  task.cancel()
                  isDone = true
                }                
              }
            else
              Cancel
          }

        def onError(ex: Throwable): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              observer.onError(ex)
            }            
          }

        def onComplete(): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              observer.onComplete()
            }
          }
      })
    }

  /**
   * Creates a new Observable that drops the events of the source, only
   * for the specified `timestamp` window.
   *
   * @param timespan the window of time during which the new Observable
   *                 is must drop the events emitted by the source
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the timeout event.
   */
  def drop(timespan: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        @volatile private[this] var shouldDrop = true
        private[this] val task =
          scheduler.scheduleOnce(timespan, {
            shouldDrop = false
          })

        def onNext(elem: T): Future[Ack] = {
          if (shouldDrop)
            Continue
          else
            observer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          task.cancel()
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          task.cancel()
          observer.onComplete()
        }
      })
    }

  /**
   * Creates a new Observable that only emits the last `n` elements
   * emitted by the source.
   */
  def takeRight(n: Int): Observable[T] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] val queue = mutable.Queue.empty[T]
        private[this] var queued = 0

        def onNext(elem: T): Future[Ack] = {
          if (n <= 0)
            Cancel
          else if (queued < n) {
            queue.enqueue(elem)
            queued += 1
          }
          else {
            queue.enqueue(elem)
            queue.dequeue()
          }
          Continue
        }

        def onError(ex: Throwable): Unit = {
          queue.clear()
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          Observable.from(queue).unsafeSubscribe(observer)
        }
      })
    }

  /**
   * Drops the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to drop
   *  @return    a new Observable that drops the first ''n'' elements
   *             emitted by the source
   */
  def drop(n: Int): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var count = 0L

        def onNext(elem: T) = {
          if (count < n) {
            count += 1
            Continue
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isValid = p(elem)
              streamError = false
              if (isValid) {
                observer.onNext(elem)
              }
              else {
                shouldContinue = false
                observer.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) {
                  observer.onError(ex)
                  Cancel
                }
                else
                  Future.failed(ex)
            }
          }
          else
            Cancel
        }

        def onComplete() = {
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(isRefTrue: AtomicBoolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            if (isRefTrue.get)
              observer.onNext(elem)
            else {
              shouldContinue = false
              observer.onComplete()
              Cancel
            }
          }
          else
            Cancel
        }

        def onComplete() = {
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Returns the values from the source Observable until the other
   * Observable produces a value.
   *
   * The second Observable can cause takeUntil to quit emitting items
   * either by emitting an event or by completing with
   * `onError` or `onCompleted`.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/create.png" />
   */
  def takeUntil[U](other: Observable[U]): Observable[T] =
    Observable.create { observer =>
      // we've got contention between the source and the other observable
      // and we can't use an Atomic here because of `onNext`, so we are
      // forced to use a lock in order to preserve the non-concurrency
      // clause in the contract
      val lock = SpinLock()
      // must be at all times synchronized by lock
      var isDone = false

      def terminate(ex: Throwable = null): Unit =
        lock.enter {
          if (!isDone) {
            isDone = true
            if (ex == null)
              observer.onComplete()
            else
              observer.onError(ex)
          }
        }

      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = lock.enter {
          if (isDone) Cancel
          else
            observer.onNext(elem).onCancel {
              lock.enter { isDone = true }
            }
        }

        def onError(ex: Throwable): Unit = terminate(ex)
        def onComplete(): Unit = terminate()
      })

      other.unsafeSubscribe(new SynchronousObserver[U] {
        def onNext(elem: U) = {
          terminate()
          Cancel
        }

        def onError(ex: Throwable) = terminate(ex)
        def onComplete() = terminate()
      })
    }

  /**
   * Drops the longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits the rest.
   */
  def dropWhile(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var continueDropping = true

        def onNext(elem: T) = {
          if (continueDropping) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isStillInvalid = p(elem)
              streamError = false

              if (isStillInvalid)
                Continue
              else {
                continueDropping = false
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Drops the longest prefix of elements that satisfy the given function
   * and returns a new Observable that emits the rest. In comparison with
   * [[dropWhile]], this version accepts a function that takes an additional
   * parameter: the zero-based index of the element.
   */
  def dropWhileWithIndex(p: (T, Int) => Boolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var continueDropping = true
        var index = 0

        def onNext(elem: T) = {
          if (continueDropping) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isStillInvalid = p(elem, index)
              streamError = false

              if (isStillInvalid) {
                index += 1
                Continue
              }
              else {
                continueDropping = false
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Creates a new Observable that emits the total number of `onNext` events
   * that were emitted by the source.
   *
   * Note that this Observable emits only one item after the source is complete.
   * And in case the source emits an error, then only that error will be
   * emitted.
   */
  def count(): Observable[Long] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] var count = 0l

        def onNext(elem: T): Future[Ack] = {
          count += 1
          Continue
        }

        def onComplete() = {
          observer.onNext(count)
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time.
   * 
   * @param count the bundle size
   */
  def buffer(count: Int): Observable[Seq[T]] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var size = 0

        def onNext(elem: T): Future[Ack] = {
          size += 1
          buffer.append(elem)
          if (size >= count) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            size = 0
            observer.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          observer.onError(ex)
          buffer = null
        }

        def onComplete(): Unit = {
          if (size > 0) {
            observer.onNext(buffer)
            observer.onComplete()
          }
          else
            observer.onComplete()
          buffer = null
        }
      })
    }

  /**
   * Periodically gather items emitted by an Observable into bundles and emit
   * these bundles rather than emitting the items one at a time.
   *
   * This version of `buffer` emits a new bundle of items periodically, 
   * every timespan amount of time, containing all items emitted by the 
   * source Observable since the previous bundle emission.
   *
   * @param timespan the interval of time at which it should emit the buffered bundle
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the `onNext` events.
   */
  def buffer(timespan: FiniteDuration)(implicit scheduler: Scheduler): Observable[Seq[T]] =
    Observable.create { observer =>
      subscribeFn(new SynchronousObserver[T] {
        private[this] val lock = SpinLock()
        private[this] var queue = ArrayBuffer.empty[T]
        private[this] var isDone = false

        private[this] val task =
          scheduler.scheduleRecursive(timespan, timespan, { reschedule =>
            lock.enter {
              if (!isDone) {
                val current = queue
                queue = ArrayBuffer.empty
                val result =
                  try observer.onNext(current) catch {
                    case NonFatal(ex) =>
                      Future.failed(ex)
                  }

                result match {
                  case sync if sync.isCompleted =>
                    sync.value.get match {
                      case Success(Continue) =>
                        reschedule()
                      case Success(Cancel) =>
                        isDone = true
                      case Failure(ex) =>
                        isDone = true
                        observer.onError(ex)
                    }
                  case async =>
                    async.onComplete {
                      case Success(Continue) =>
                        lock.enter {
                          if (!isDone) reschedule
                        }
                      case Success(Cancel) =>
                        lock.enter {
                          isDone = true
                        }
                      case Failure(ex) =>
                        lock.enter {
                          isDone = true
                          observer.onError(ex)
                        }
                    }
                }
              }
            }
          })

        def onNext(elem: T): Ack = lock.enter {
          if (!isDone) {
            queue.append(elem)
            Continue
          }
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = lock.enter {
          if (!isDone) {
            isDone = true
            queue = null
            observer.onError(ex)
            task.cancel()
          }
        }

        def onComplete(): Unit = lock.enter {
          if (!isDone) {
            if (queue.nonEmpty) {
              observer.onNext(queue)
              observer.onComplete()
            }
            else
              observer.onComplete()

            isDone = true
            queue = null
            task.cancel()
          }
        }
      })
    }


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
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the sample events.
   */
  def sample(delay: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
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
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the sample events.
   */
  def sample(initialDelay: FiniteDuration, delay: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new SynchronousObserver[T] {
        private[this] val lock = SpinLock()
        // must be synchronized by lock
        private[this] var isDone = false
        // must be synchronized by lock
        private[this] var lastEvent: T = _
        // must be synchronized by lock
        private[this] var valueHappened = false

        private[this] val task =
          scheduler.scheduleRecursive(initialDelay, delay, { reschedule =>
            lock.enter {
              if (!isDone) {
                if (valueHappened) {
                  valueHappened = false
                  val result =
                    try observer.onNext(lastEvent) catch {
                      case NonFatal(ex) =>
                        Future.failed(ex)
                    }

                  result match {
                    case sync if sync.isCompleted =>
                      sync.value.get match {
                        case Success(Continue) =>
                          reschedule()
                        case Success(Cancel) =>
                          isDone = true
                        case Failure(ex) =>
                          onError(ex)
                      }
                    case async =>
                      async.onComplete {
                        case Success(Continue) =>
                          reschedule()
                        case Success(Cancel) =>
                          lock.enter { isDone = true }
                        case Failure(ex) =>
                          onError(ex)
                      }
                  }
                }
                else {
                  reschedule()
                }
              }
            }
          })

        def onNext(elem: T) =
          lock.enter {
            if (isDone) Cancel
            else {
              valueHappened = true
              lastEvent = elem
              Continue
            }
          }

        def onError(ex: Throwable): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              task.cancel()
              observer.onError(ex)
            }
          }

        def onComplete(): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              task.cancel()
              observer.onComplete()
            }
          }
      })
    }

  /**
   * Creates an Observable that emits the events emitted by the source, shifted
   * forward in time, specified by the given `itemDelay`.
   *
   * Note: the [[BufferPolicy.default default policy]] is being used for buffering.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/delay.png" />
   *
   * @param itemDelay is the period of time to wait before events start
   *                   being signaled
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the timeout.
   */
  def delay(itemDelay: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    delay(defaultPolicy, itemDelay)

  /**
   * Creates an Observable that emits the events emitted by the source, shifted
   * forward in time, specified by the given `itemDelay`.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/delay.png" />
   *
   * @param policy is the policy used for buffering, see [[BufferPolicy]]
   *
   * @param itemDelay is the period of time to wait before events start
   *                   being signaled
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the timeout.
   */
  def delay(policy: BufferPolicy, itemDelay: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    self.delay(policy, { (connect, _) =>
      scheduler.scheduleOnce(itemDelay, connect())
    })

  /**
   * Creates an Observable that emits the events emitted by the source, shifted
   * forward in time, the streaming being triggered by the completion of a future.
   *
   * During the delay period the emitted elements are being buffered.
   * If the future is completed with a failure, then the error
   * will get streamed directly to our observable, bypassing any buffered
   * events that we may have.
   *
   * Note: the [[BufferPolicy.default default policy]] is being used for buffering.
   *
   * @param future is a `Future` that starts the streaming upon completion
   */
  def delay(future: Future[_]): Observable[T] = {
    delay(defaultPolicy, future)
  }

  /**
   * Creates an Observable that emits the events emitted by the source, shifted
   * forward in time, the streaming being triggered by the completion of a future.
   *
   * During the delay period the emitted elements are being buffered.
   * If the future is completed with a failure, then the error
   * will get streamed directly to our observable, bypassing any buffered
   * events that we may have.
   *
   * @param policy is the policy used for buffering, see [[BufferPolicy]]
   *
   * @param future is a `Future` that starts the streaming upon completion
   */
  def delay(policy: BufferPolicy, future: Future[_]): Observable[T] = {
    delay(policy, { (connect, signalError) =>
      val task = BooleanCancelable()
      future.onComplete {
        case Success(_) =>
          if (!task.isCanceled)
            connect()
        case Failure(ex) =>
          if (!task.isCanceled)
            signalError(ex)
      }

      task
    })
  }

  /**
   * Creates an Observable that emits the events emitted by the source
   * shifted forward in time, delay introduced by waiting for an event
   * to happen, an event initiated by the given callback.
   *
   * Example 1:
   * {{{
   *   Observable.interval(1.second)
   *     .delay { (connect, signalError) =>
   *       val task = BooleanCancelable()
   *       future.onComplete {
   *         case Success(_) =>
   *           if (!task.isCanceled)
   *             connect()
   *         case Failure(ex) =>
   *           if (!task.isCanceled)
   *             signalError(ex)
   *       }
   *
   *       task
   *     }
   * }}}
   *
   * In the above example, upon subscription the given function gets called,
   * scheduling the streaming to start after a certain future is completed.
   * During that wait period the events are buffered and after the
   * future is finally completed, the buffer gets streamed to the observer,
   * after which streaming proceeds as normal.
   *
   * Example 2:
   * {{{
   *   Observable.interval(1.second)
   *     .delay { (connect, handleError) =>
   *       scheduler.schedule(5.seconds, {
   *         connect()
   *       })
   *     }
   * }}}
   *
   * In the above example, upon subscription the given function gets called,
   * scheduling a task to execute after 5 seconds. During those 5 seconds
   * the events are buffered and after those 5 seconds are elapsed, the
   * buffer gets streamed, after which streaming proceeds as normal.
   *
   * Notes:
   *
   * - only `onNext` and `onComplete` events are delayed, but not `onError`,
   *   as `onError` interrupts the delay and streams the error as soon as it can.
   * - the [[BufferPolicy.default default policy]] is being used for
   *   buffering.
   *
   * @param init is the function that gets called for initiating the event
   *             that finally starts the streaming sometime in the future - it
   *             takes 2 arguments, a function that must be called to start the
   *             streaming and an error handling function that must be called
   *             in case an error happened while waiting
   */
  def delay(init: (() => Unit, Throwable => Unit) => Cancelable): Observable[T] =
    delay(defaultPolicy, init)

  /**
   * Creates an Observable that emits the events emitted by the source
   * shifted forward in time, delay introduced by waiting for an event
   * to happen, an event initiated by the given callback.
   *
   * Example 1:
   * {{{
   *   Observable.interval(1.second)
   *     .delay(Unbounded, { (connect, signalError) =>
   *       val task = BooleanCancelable()
   *       future.onComplete {
   *         case Success(_) =>
   *           if (!task.isCanceled)
   *             connect()
   *         case Failure(ex) =>
   *           if (!task.isCanceled)
   *             signalError(ex)
   *       }
   *       task
   *     })
   * }}}
   *
   * In the above example, upon subscription the given function gets called,
   * scheduling the streaming to start after a certain future is completed.
   * During that wait period the events are buffered and after the
   * future is finally completed, the buffer gets streamed to the observer,
   * after which streaming proceeds as normal.
   *
   * Example 2:
   * {{{
   *   Observable.interval(1.second)
   *     .delay(Unbounded, { (connect, handleError) =>
   *       scheduler.schedule(5.seconds, {
   *         connect()
   *       })
   *     })
   * }}}
   *
   * In the above example, upon subscription the given function gets called,
   * scheduling a task to execute after 5 seconds. During those 5 seconds
   * the events are buffered and after those 5 seconds are elapsed, the
   * buffer gets streamed, after which streaming proceeds as normal.
   *
   * Notes:
   *
   * - if an error happens while waiting for our event to get triggered, it can
   *   be signaled with `handleError` (see sample above)
   * - only `onNext` and `onComplete` events are delayed, but not `onError`,
   *   as `onError` interrupts the delay and streams the error as soon as it can.
   *
   * @param policy is the buffering policy used, see [[BufferPolicy]]
   *
   * @param init is the function that gets called for initiating the event
   *             that finally starts the streaming sometime in the future - it
   *             takes 2 arguments, a function that must be called to start the
   *             streaming and an error handling function that must be called
   *             in case an error happened while waiting
   */
  def delay(policy: BufferPolicy, init: (() => Unit, Throwable => Unit) => Cancelable): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val lock = SpinLock()
        // MUST BE synchronized by lock at all times
        private[this] var buffer = ArrayBuffer.empty[T]
        // SHOULD BE synchronized by lock if false, if true then
        // the logic can take the fast path and avoid lock synchronization
        private[this] var isConnected = false
        // signals that the downstream observer has canceled the stream
        // during buffer draining, set to true after isConnected = true and
        // before connectedF is completed
        private[this] var isCanceledDuringFeeding = false
        // Promise to be completed after the observer has been fed
        // with the whole buffer
        private[this] val connectedP = Promise[Ack]()
        // Future to be completed after the observer has been fed
        // with the whole buffer - reference must be mutable, because
        // that flatMap-ing in onNext can become concurrent with
        // onComplete / onError, so it must be changed to reflect
        // the latest acknowledgement of `observer.onNext(elem)`
        private[this] var connectedF = connectedP.future
        // cancelable of the task that has to run for executing `connect`,
        // initialized either in onNext or in onComplete.
        private[this] val task = SingleAssignmentCancelable()
        // value to be set to false after the first item being signaled
        // accessed from onNext/onComplete so does not need synchronization
        private[this] var isTaskInitialized = false

        /** Initializes scheduled task, to be called in onNext/onComplete */
        @inline private[this] def initializeTask(): Unit = {
          if (!isTaskInitialized) {
            task := init(connect, onError)
            isTaskInitialized = true
          }
        }

        private[this] def connect(): Unit = lock.enter {
          if (!isConnected) {
            // signals that the observer has been connected to the stream
            // and thus future events mustn't be buffered
            isConnected = true

            Observer.feed(observer, buffer).onCompleteNow {
              case ack @ Success(Continue | Cancel) =>
                lock.enter {
                  isCanceledDuringFeeding = ack == Cancel.IsSuccess
                  buffer = null // GC purposes
                  connectedP.complete(ack)
                }
              case Failure(ex) =>
                isCanceledDuringFeeding = true
                observer.onError(ex)
                buffer = null // GC purposes
                connectedP.success(Cancel)
            }
          }
        }

        def onNext(elem: T): Future[Ack] = {
          if (isConnected && connectedF.isCompleted) {
            // fast path branch taken when the observer is connected
            // and the whole buffer has been drained
            if (!isCanceledDuringFeeding) {
              connectedF = observer.onNext(elem)
              connectedF
            }
            else {
              Cancel
            }
          }
          else if (isConnected) {
            // slow path branch taken when the observer is connected,
            // but the buffer draining is in progress, so we must still do
            // buffering, this time piggybacking on the execution context
            connectedF = connectedF.flatMap {
              case Cancel => Cancel
              case Continue =>
                observer.onNext(elem)
            }
            connectedF
          }
          else lock.enter {
            // race condition guard
            if (isConnected)
              onNext(elem) // retry
            else {
              // initializing the task that will eventually connect
              // the buffer to the downstream observer
              initializeTask()

              // we are still within the delay period and hence we are still
              // buffering incoming events, but we can only do buffering
              // according to the requested policy
              policy match {
                case Unbounded =>
                  // unbounded means buffer everything
                  buffer.append(elem)
                  Continue

                case OverflowTriggering(capacity) =>
                  if (buffer.length < capacity) {
                    buffer.append(elem)
                    Continue
                  }
                  else {
                    // if over capacity, then trigger error
                    onError(new BufferOverflowException(
                      s"Reached buffer capacity of $capacity during delay, " +
                        "cannot buffer any more items"
                    ))
                    Cancel
                  }

                case BackPressured(capacity) =>
                  if (buffer.length < capacity) {
                    buffer.append(elem)
                    Continue
                  }
                  else {
                    // if over capacity, then do back-pressure
                    connectedF = connectedF.flatMap {
                      case Cancel => Cancel
                      case Continue =>
                        observer.onNext(elem)
                    }
                    connectedF
                  }
              }
            }
          }
        }

        def onComplete(): Unit = {
          initializeTask()
          // the complete event gets scheduled to run after the
          // observer has been connected and the buffer has
          // been drained - careful here, since this logic can end up
          // running concurrent with the flatMap-ing in onNext
          connectedF.onContinueComplete(observer)
        }

        def onError(ex: Throwable): Unit = lock.enter {
          if (!isConnected) {
            // we are not connected yet, so cancel the buffer
            // and stream the error immediately
            task.cancel()
            buffer = null // GC purposes
            isConnected = true
            observer.onError(ex)
          }
          else {
            // we are connected, so we must stream the error only after
            // the buffer has been drained - careful here, since this logic
            // can end up running concurrently with the flatMap-ing in onNext
            connectedF.onContinueComplete(observer, ex)
          }
        }
      })
    }

  /**
   * Hold an Observer's subscription request until the given `future` completes,
   * before passing it on to the source Observable. If the given `future`
   * completes in error, then the subscription is terminated with `onError`.
   *
   * @param future the `Future` that must complete in order for the
   *               subscription to happen.
   */
  def delaySubscription(future: Future[_]): Observable[T] =
    Observable.create { observer =>
      future.onComplete {
        case Success(_) =>
          unsafeSubscribe(observer)
        case Failure(ex) =>
          observer.onError(ex)
      }
    }

  /**
   * Hold an Observer's subscription request for a specified
   * amount of time before passing it on to the source Observable.
   *
   * @param timespan is the time to wait before the subscription
   *                 is being initiated.
   *
   * @param scheduler is the [[monifu.concurrent.Scheduler Scheduler]] needed
   *                  for triggering the timeout event.
   */
  def delaySubscription(timespan: FiniteDuration)(implicit scheduler: Scheduler): Observable[T] =
    delaySubscription {
      val p = Promise[Unit]()
      scheduler.scheduleOnce(timespan, p.success(()))
      p.future
    }

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            state = op(state, elem)
            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onComplete() = {
          observer.onNext(state)
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onComplete`.
   */
  def reduce[U >: T](op: (U, U) => U): Observable[U] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var state: U = _
        private[this] var isFirst = true
        private[this] var wasApplied = false

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            if (isFirst) {
              isFirst = false
              state = elem
            }
            else {
              state = op(state, elem)
              if (!wasApplied) wasApplied = true
            }

            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onComplete() = {
          if (wasApplied) {
            observer.onNext(state)
            observer.onComplete()
          }
          else
            observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits on each step the result
   * of the applied function.
   *
   * Similar to [[foldLeft]], but emits the state on each step. Useful for modeling finite
   * state machines.
   */
  def scan[R](initial: R)(op: (R, T) => R): Observable[R] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            state = op(state, elem)
            streamError = false
            observer.onNext(state)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

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
  def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Observable[R] =
    Observable.create { observer =>
      subscribeFn(new Observer[T] {
        private[this] val refCount = RefCountCancelable(observer.onComplete())
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          val refID = refCount.acquire()
          val upstreamPromise = Promise[Ack]()
          var streamError = true

          try {
            val newState = op(state, elem)
            streamError = false

            newState.unsafeSubscribe(new Observer[R] {
              private[this] var ack = Continue : Future[Ack]

              def onNext(elem: R): Future[Ack] = {
                state = elem
                ack = observer.onNext(elem)
                  .ifCancelTryCanceling(upstreamPromise)
                ack
              }

              def onError(ex: Throwable): Unit = {
                if (upstreamPromise.trySuccess(Cancel))
                  observer.onError(ex)
              }

              def onComplete(): Unit =
                ack.onContinue {
                  refID.cancel()
                  upstreamPromise.trySuccess(Continue)
                }
            })
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) {
                observer.onError(ex)
                Cancel
              }
              else
                Future.failed(ex)
          }

          upstreamPromise.future
        }

        def onComplete() = {
          refCount.cancel()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Executes the given callback when the stream has ended
   * (after the event was already emitted).
   *
   * NOTE: protect the callback such that it doesn't throw exceptions, because
   * it gets executed after `onComplete()` happens and by definition the error cannot
   * be streamed with `onError()`.
   *
   * @param cb the callback to execute when the subscription is canceled
   */
  def doOnComplete(cb: => Unit): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val wasExecuted = Atomic(false)

        private[this] def execute() = {
          if (wasExecuted.compareAndSet(expect=false, update=true))
            try cb catch {
              case NonFatal(ex) =>
                context.reportFailure(ex)
            }
        }

        def onNext(elem: T) = {
          val f = observer.onNext(elem)
          f.onSuccess { case Cancel => execute() }
          f
        }

        def onError(ex: Throwable): Unit = {
          try observer.onError(ex) finally execute()
        }

        def onComplete(): Unit = {
          try observer.onComplete() finally execute()
        }
      })
    }

  /**
   * Executes the given callback for each element generated by the source
   * Observable, useful for doing side-effects.
   *
   * @return a new Observable that executes the specified callback for each element
   */
  def doWork(cb: T => Unit): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()

        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            cb(elem)
            streamError = false
            observer.onNext(elem)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }
      })
    }

  /**
   * Executes the given callback only for the first element generated by the source
   * Observable, useful for doing a piece of computation only when the stream started.
   *
   * @return a new Observable that executes the specified callback only for the first element
   */
  def doOnStart(cb: T => Unit): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var isStarted = false

        def onNext(elem: T) = {
          if (!isStarted) {
            isStarted = true
            var streamError = true
            try {
              cb(elem)
              streamError = false
              observer.onNext(elem)
            }
            catch {
              case NonFatal(ex) =>
                observer.onError(ex)
                Cancel
            }
          }
          else
            observer.onNext(elem)
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

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
   * Returns an Observable that emits a single boolean, either true, in case the given predicate holds for all the items
   * emitted by the source, or false in case at least one item is not verifying the given predicate.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for all the items
   */
  def forAll(p: T => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  /**
   * Returns an Observable that doesn't emit anything, but that completes when the source Observable completes.
   */
  def complete: Observable[Nothing] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onError(ex: Throwable): Unit =
          observer.onError(ex)
        def onComplete(): Unit =
          observer.onComplete()
      })
    }

  /**
   * Returns an Observable that emits a single Throwable, in case an error was thrown by the source Observable,
   * otherwise it isn't going to emit anything.
   */
  def error: Observable[Throwable] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onComplete(): Unit =
          observer.onComplete()

        def onError(ex: Throwable): Unit = {
          observer.onNext(ex)
          observer.onComplete()
        }
      })
    }

  /**
   * Emits the given exception instead of `onComplete`.
   * @param error the exception to emit onComplete
   * @return a new Observable that emits an exception onComplete
   */
  def endWithError(error: Throwable): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = observer.onNext(elem)
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onError(error)
      })
    }

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
    Observable.from(elems) ++ this

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
    this ++ Observable.from(elems)

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
  def last: Observable[T] = takeRight(1)

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
    Observable.create { observerOfPairs =>
      // using mutability, receiving data from 2 producers, so must synchronize
      val lock = SpinLock()
      val queueA = mutable.Queue.empty[(Promise[U], Promise[Ack])]
      val queueB = mutable.Queue.empty[(U, Promise[Ack])]
      var isCompleted = false
      var ack = Continue : Future[Ack]

      def _onError(ex: Throwable) = lock.enter {
        if (!isCompleted) {
          isCompleted = true
          queueA.clear()
          queueB.clear()
          observerOfPairs.onError(ex)
        }
      }

      unsafeSubscribe(new Observer[T] {
        def onNext(a: T): Future[Ack] =
          lock.enter {
            if (isCompleted)
              Cancel
            else if (queueB.isEmpty) {
              val resp = Promise[Ack]()
              val promiseForB = Promise[U]()
              queueA.enqueue((promiseForB, resp))

              ack = promiseForB.future.flatMap(b => observerOfPairs.onNext((a, b)))
              resp.completeWith(ack)
              ack
            }
            else {
              val (b, bResponse) = queueB.dequeue()
              val f = observerOfPairs.onNext((a, b))
              bResponse.completeWith(f)
              f
            }
          }

        def onError(ex: Throwable) =
          _onError(ex)

        def onComplete() =
          ack.onContinue {
            lock.enter {
              if (!isCompleted && queueA.isEmpty) {
                isCompleted = true
                queueA.clear()
                queueB.clear()
                observerOfPairs.onComplete()
              }
            }
          }
      })

      other.unsafeSubscribe(new Observer[U] {
        def onNext(b: U): Future[Ack] =
          lock.enter {
            if (isCompleted)
              Cancel
            else if (queueA.nonEmpty) {
              val (bPromise, response) = queueA.dequeue()
              bPromise.success(b)
              response.future
            }
            else {
              val p = Promise[Ack]()
              queueB.enqueue((b, p))
              p.future
            }
          }

        def onError(ex: Throwable) = _onError(ex)

        def onComplete() =
          ack.onContinue {
            lock.enter {
              if (!isCompleted && queueB.isEmpty) {
                isCompleted = true
                queueA.clear()
                queueB.clear()
                observerOfPairs.onComplete()
              }
            }
          }
      })
    }

  /**
   * Takes the elements of the source Observable and emits the maximum value,
   * after the source has completed.
   */
  def max[U >: T](implicit ev: Ordering[U]): Observable[U] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var maxValue: T = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            maxValue = elem
          }
          else if (ev.compare(elem, maxValue) > 0) {
            maxValue = elem
          }
          Continue
        }

        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            observer.onComplete()
          else {
            observer.onNext(maxValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the maximum key value, where the key is generated by the given function `f`.
   */
  def maxBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var maxValue: T = _
        private[this] var maxValueU: U = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            maxValue = elem
            maxValueU = f(elem)
          }
          else {
            val m = f(elem)
            if (ev.compare(m, maxValueU) > 0) {
              maxValue = elem
              maxValueU = m
            }
          }
          Continue
        }

        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            observer.onComplete()
          else {
            observer.onNext(maxValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Takes the elements of the source Observable and emits the minimum value,
   * after the source has completed.
   */
  def min[U >: T](implicit ev: Ordering[U]): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var minValue: T = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            minValue = elem
          }
          else if (ev.compare(elem, minValue) < 0) {
            minValue = elem
          }
          Continue
        }

        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            observer.onComplete()
          else {
            observer.onNext(minValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Takes the elements of the source Observable and emits the element that has
   * the minimum key value, where the key is generated by the given function `f`.
   */
  def minBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var minValue: T = _
        private[this] var minValueU: U = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            minValue = elem
            minValueU = f(elem)
          }
          else {
            val m = f(elem)
            if (ev.compare(m, minValueU) < 0) {
              minValue = elem
              minValueU = m
            }
          }
          Continue
        }

        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            observer.onComplete()
          else {
            observer.onNext(minValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Given a source that emits numeric values, the `sum` operator
   * sums up all values and at onComplete it emits the total.
   */
  def sum[U >: T](implicit ev: Numeric[U]): Observable[U] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var result = ev.zero

        def onNext(elem: T): Future[Ack] = {
          result = ev.plus(result, elem)
          Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete(): Unit = {
          observer.onNext(result)
          observer.onComplete()
        }
      })
    }

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for listening to the emitted items.
   *
   * @param ec the execution context on top of which the generated `onNext` / `onComplete` / `onError` events will run
   *
   * @param bufferPolicy specifies the buffering policy used by the created asynchronous boundary
   */
  def observeOn(ec: ExecutionContext, bufferPolicy: BufferPolicy = defaultPolicy): Observable[T] = {
    implicit val context = ec
    Observable.create(observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val buffer =
          BufferedObserver(observer, bufferPolicy)

        def onNext(elem: T): Future[Ack] = {
          buffer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          buffer.onError(ex)
        }

        def onComplete(): Unit = {
          buffer.onComplete()
        }
      }))
  }

  /**
   * Suppress the duplicate elements emitted by the source Observable.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct: Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[T]

        def onNext(elem: T) = {
          if (set(elem)) Continue
          else {
            set += elem
            observer.onNext(elem)
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Given a function that returns a key for each element emitted by
   * the source Observable, suppress duplicates items.
   *
   * WARNING: this requires unbounded buffering.
   */
  def distinct[U](fn: T => U): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[U]

        def onNext(elem: T) = {
          val key = fn(elem)
          if (set(key)) Continue
          else {
            set += key
            observer.onNext(elem)
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged: Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastElem: T = _

        def onNext(elem: T) = {
          if (isFirst) {
            lastElem = elem
            isFirst = false
            observer.onNext(elem)
          }
          else if (lastElem != elem) {
            lastElem = elem
            observer.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Suppress duplicate consecutive items emitted by the source Observable
   */
  def distinctUntilChanged[U](fn: T => U): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastKey: U = _

        def onNext(elem: T) = {
          val key = fn(elem)
          if (isFirst) {
            lastKey = fn(elem)
            isFirst = false
            observer.onNext(elem)
          }
          else if (lastKey != key) {
            lastKey = key
            observer.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for initiating the subscription.
   */
  def subscribeOn(ec: ExecutionContext): Observable[T] = {
    Observable.create(o => ec.execute(new Runnable {
      def run() = unsafeSubscribe(o)
    }))
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
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var ack = Continue : Future[Ack]

        def onNext(elem: T): Future[Ack] = {
          ack = observer.onNext(OnNext(elem))
          ack
        }          

        def onError(ex: Throwable): Unit = 
          ack.onContinue {
            observer.onNext(OnError(ex))
            observer.onComplete()            
          }

        def onComplete(): Unit = 
          ack.onContinue {
            observer.onNext(OnComplete)
            observer.onComplete()            
          }
      })
    }

  /**
   * Utility that can be used for debugging purposes.
   */
  def dump(prefix: String): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var pos = 0

        def onNext(elem: T): Future[Ack] = {
          System.out.println(s"$pos: $prefix-->$elem")
          pos += 1
          val f = observer.onNext(elem)
          f.onCancel { pos += 1; System.out.println(s"$pos: $prefix canceled") }
          f
        }

        def onError(ex: Throwable) = {
          System.out.println(s"$pos: $prefix-->$ex")
          pos += 1
          observer.onError(ex)
        }

        def onComplete() = {
          System.out.println(s"$pos: $prefix completed")
          pos += 1
          observer.onComplete()
        }
      })
    }

  /**
   * Repeats the items emitted by this Observable continuously. It caches the generated items until `onComplete`
   * and repeats them ad infinitum. On error it terminates.
   */
  def repeat: Observable[T] = {
    def loop(subject: Subject[T, T], observer: Observer[T]): Unit =
      subject.unsafeSubscribe(new Observer[T] {
        private[this] var lastResponse = Continue : Future[Ack]
        def onNext(elem: T) = {
          lastResponse = observer.onNext(elem)
          lastResponse
        }
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete(): Unit = {
          lastResponse.onContinue(loop(subject, observer))
        }
      })

    Observable.create { observer =>
      val subject = ReplaySubject[T]()
      loop(subject, observer)

      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subject.onNext(elem)
        }
        def onError(ex: Throwable): Unit = {
          subject.onError(ex)
        }
        def onComplete(): Unit = {
          subject.onComplete()
        }
      })
    }
  }

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers).
   */
  def multicast[R](subject: Subject[T, R]): ConnectableObservable[R] =
    new ConnectableObservable[R] {
      private[this] val notCanceled = Atomic(true)
      implicit val context = self.context

      private[this] val cancelAction =
        BooleanCancelable { notCanceled set false }
      private[this] val notConnected =
        Cancelable { self.takeWhile(notCanceled).unsafeSubscribe(subject) }

      def connect() = {
        notConnected.cancel()
        cancelAction
      }

      def subscribeFn(observer: Observer[R]): Unit = {
        subject.unsafeSubscribe(observer)
      }
    }

  /**
   * Wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.SafeObserver SafeObserver]].
   * Normally wrapping in a `SafeObserver` happens at the edges of the monad
   * (in the user-facing `subscribe()` implementation) or in Observable subscribe implementations,
   * so this wrapping is useful.
   */
  def safe: Observable[T] =
    Observable.create { observer => unsafeSubscribe(SafeObserver(observer)) }

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
   * back-pressure requirements and are problematic, see [[async]] and
   * [[monifu.reactive.BufferPolicy BufferPolicy]] for options.
   */
  def concurrent: Observable[T] =
    Observable.create { observer => unsafeSubscribe(ConcurrentObserver(observer)) }

  /**
   * Forces a buffered asynchronous boundary.
   *
   * Internally it wraps the observer implementation given to `subscribeFn` into a
   * [[monifu.reactive.observers.BufferedObserver BufferedObserver]].
   *
   * Normally Monifu's implementation guarantees that events are not emitted concurrently,
   * and that the publisher MUST NOT emit the next event without acknowledgement from the consumer
   * that it may proceed, however for badly behaved publishers, this wrapper provides
   * the guarantee that the downstream [[monifu.reactive.Observer Observer]] given in `subscribe` will not receive
   * concurrent events.
   *
   * Compared with [[concurrent]] / [[monifu.reactive.observers.ConcurrentObserver ConcurrentObserver]], the acknowledgement
   * given by [[monifu.reactive.observers.BufferedObserver BufferedObserver]] can be synchronous
   * (i.e. the `Future[Ack]` is already completed), so the publisher can send the next event without waiting for
   * the consumer to receive and process the previous event (i.e. the data source will receive the `Continue`
   * acknowledgement once the event has been buffered, not when it has been received by its destination).
   *
   * WARNING: if the buffer created by this operator is unbounded, it can blow up the process if the data source
   * is pushing events faster than what the observer can consume, as it introduces an asynchronous
   * boundary that eliminates the back-pressure requirements of the data source. Unbounded is the default
   * [[monifu.reactive.BufferPolicy policy]], see [[monifu.reactive.BufferPolicy BufferPolicy]]
   * for options.
   */
  def async(policy: BufferPolicy = defaultPolicy): Observable[T] =
    Observable.create { observer => unsafeSubscribe(BufferedObserver(observer, policy)) }

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def publish(): ConnectableObservable[T] =
    multicast(PublishSubject())

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
   */
  def behavior[U >: T](initialValue: U): ConnectableObservable[U] =
    multicast(BehaviorSubject(initialValue))

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def replay(): ConnectableObservable[T] =
    multicast(ReplaySubject())

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers). The underlying subject used is a
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def publishLast(): ConnectableObservable[T] =
    multicast(AsyncSubject())

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
  def asFuture: Future[Option[T]] = {
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
  def foreach(cb: T => Unit): Unit =
    unsafeSubscribe(new Observer[T] {
      def onNext(elem: T) =
        try { cb(elem); Continue } catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }

      def onComplete() = ()
      def onError(ex: Throwable) = {
        context.reportFailure(ex)
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
   *   def emit[T](elem: T, nrOfTimes: Int)(implicit ec: ExecutionContext): Observable[T] =
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
  def create[T](f: Observer[T] => Unit)(implicit ec: ExecutionContext): Observable[T] ={
    new Observable[T] {
      implicit val context = ec
      override def subscribeFn(observer: Observer[T]): Unit =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
        }
    }
  }

  /**
   * Creates an observable that doesn't emit anything, but immediately calls `onComplete`
   * instead.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/empty.png" />
   */
  def empty[A](implicit ec: ExecutionContext): Observable[A] =
    Observable.create { observer =>
      SafeObserver(observer).onComplete()
    }

  /**
   * Creates an Observable that only emits the given ''a''
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/unit.png" />
   */
  def unit[A](elem: A)(implicit ec: ExecutionContext): Observable[A] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      observer.onNext(elem)
      observer.onComplete()
    }
  }

  /**
   * Creates an Observable that emits an error.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/error.png" />
   */
  def error(ex: Throwable)(implicit ec: ExecutionContext): Observable[Nothing] =
    Observable.create { observer =>
      SafeObserver[Nothing](observer).onError(ex)
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/never.png"" />
   */
  def never(implicit ec: ExecutionContext): Observable[Nothing] =
    Observable.create { _ => () }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param period the delay between two subsequent events
   * @param ec the execution context in which `onNext` will get called
   * @param s the scheduler used for scheduling the periodic signaling of onNext
   */
  def interval(period: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): Observable[Long] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      var counter = 0

      s.scheduleRecursive(Duration.Zero, period, { reschedule =>
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
  def repeat[T](elems: T*)(implicit ec: ExecutionContext): Observable[T] = {
    if (elems.size == 0) Observable.empty
    else if (elems.size == 1) {
      Observable.create { o =>
        val observer = SafeObserver(o)

        def loop(elem: T): Unit =
          ec.execute(new Runnable {
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
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/range.png" />
   *
   * @param from the range start
   * @param until the range end
   * @param step increment step, either positive or negative
   */
  def range(from: Int, until: Int, step: Int = 1)(implicit ec: ExecutionContext): Observable[Int] = {
    require(step != 0, "step must be a number different from zero")

    Observable.create { o =>
      def scheduleLoop(from: Int, until: Int, step: Int): Unit =
        ec.execute(new Runnable {
          private[this] val observer = SafeObserver(o)

          private[this] def isInRange(x: Int): Boolean = {
            (step > 0 && x < until) || (step < 0 && x > until)
          }

          @tailrec
          def loop(from: Int, until: Int, step: Int): Unit =
            if (isInRange(from)) {
              observer.onNext(from) match {
                case sync if sync.isCompleted =>
                  if (sync == Continue || sync.value.get == Continue.IsSuccess)
                    loop(from + step, until, step)
                case async =>
                  if (isInRange(from + step))
                    async.onSuccess {
                      case Continue =>
                        scheduleLoop(from + step, until, step)
                    }
                  else
                    observer.onComplete()
              }
            }
            else
              observer.onComplete()

          def run(): Unit = {
            loop(from, until, step)
          }
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
  def apply[T](elems: T*)(implicit ec: ExecutionContext): Observable[T] = {
    from(elems)
  }

  /**
   * Converts a Future to an Observable.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](future: Future[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      future.onComplete {
        case Success(value) =>
          observer.onNext(value)
          observer.onComplete()
        case Failure(ex) =>
          observer.onError(ex)
      }
    }

  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](iterable: Iterable[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      def startFeedLoop(iterator: Iterator[T]): Unit =
        ec.execute(new Runnable {
          @tailrec
          def fastLoop(): Unit = {
            val ack = observer.onNext(iterator.next())
            if (iterator.hasNext)
              ack match {
                case sync if sync.isCompleted =>
                  if (sync == Continue || sync.value.get == Continue.IsSuccess)
                    fastLoop()
                case async =>
                  async.onSuccess {
                    case Continue =>
                      startFeedLoop(iterator)
                  }
              }
            else
              observer.onComplete()
          }

          def run(): Unit = {
            try fastLoop() catch {
              case NonFatal(ex) =>
                observer.onError(ex)
            }
          }
        })

      val iterator = iterable.iterator
      if (iterator.hasNext) startFeedLoop(iterator) else observer.onComplete()
    }

  /**
   * Create an Observable that emits a single item after a given delay.
   */
  def timer[T](delay: FiniteDuration, unit: T)(implicit ec: ExecutionContext, s: Scheduler): Observable[T] =
    Observable.create { observer =>
      s.scheduleOnce(delay, {
        observer.onNext(unit)
        observer.onComplete()
      })
    }

  /**
   * Create an Observable that repeatedly emits the given `item`, until
   * the underlying Observer cancels.
   */
  def timer[T](initialDelay: FiniteDuration, period: FiniteDuration, unit: T)
      (implicit ec: ExecutionContext, s: Scheduler): Observable[T] = {

    Observable.create { observer =>
      val safeObserver = SafeObserver(observer)

      s.scheduleRecursive(initialDelay, period, { reschedule =>
        safeObserver.onNext(unit).onContinue {
          reschedule()
        }
      })
    }
  }

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Observable.from(sources).flatten

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def merge[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Observable.from(sources).merge()

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
  def concat[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Observable.from(sources).concat

  /**
   * Given a list of source Observables, emits all of the items from the first of
   * these Observables to emit an item and cancel the rest.
   */
  def amb[T](source: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] = {
    // helper function used for creating a subscription that uses `finishLine` as guard
    def createSubscription(observable: Observable[T], observer: Observer[T], finishLine: AtomicInt, idx: Int): Unit =
      observable.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onNext(elem)
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onComplete()
        }
      })

    Observable.create { observer =>
      val finishLine = Atomic(0)
      var idx = 0
      for (observable <- source) {
        createSubscription(observable, observer, finishLine, idx + 1)
        idx += 1
      }

      // if the list of observables was empty, just
      // emit `onComplete`
      if (idx == 0) observer.onComplete()
    }
  }

  /**
   * Implicit conversion from Future to Observable.
   */
  implicit def FutureIsObservable[T](future: Future[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.from(future)

  /**
   * Implicit conversion from Observable to Publisher.
   */
  implicit def ObservableIsPublisher[T](source: Observable[T]): Publisher[T] =
    source.publisher
}
