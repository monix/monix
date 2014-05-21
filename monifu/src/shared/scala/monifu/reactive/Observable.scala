package monifu.reactive

import language.implicitConversions
import monifu.concurrent.{Cancelable, Scheduler}
import scala.concurrent.{Promise, Future}
import scala.concurrent.Future.successful
import monifu.reactive.api._
import Ack.{Done, Continue}
import monifu.concurrent.atomic.{Atomic, padded}
import monifu.reactive.cancelables._
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.collection.mutable
import scala.annotation.tailrec
import collection.JavaConverters._
import scala.util.{Failure, Success}
import monifu.reactive.subjects.{BehaviorSubject, PublishSubject, Subject}
import monifu.concurrent.extensions._
import monifu.reactive.api.Notification.{OnComplete, OnNext, OnError}
import monifu.reactive.internals.AckBuffer


/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] { self =>
  /**
   * Function that creates the actual subscription when calling `subscribe`,
   * and that starts the stream, being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Rx grammar.
   *
   * @return a cancelable that can be used to cancel the streaming
   */
  def subscribe(observer: Observer[T]): Unit

  /**
   * Implicit scheduler required for asynchronous boundaries.
   */
  implicit def scheduler: Scheduler

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribe(nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Unit =
    subscribe(new Observer[T] {
      def onNext(elem: T): Future[Ack] =
        try {
          nextFn(elem)
          Continue
        }
        catch {
          case NonFatal(ex) =>
            onError(ex)
        }

      def onError(ex: Throwable) =
        try {
          errorFn(ex)
          Done
        }
        catch {
          case NonFatal(e) =>
            Future.failed(e)
        }

      def onCompleted() =
        try {
          completedFn()
          Done
        }
        catch {
          case NonFatal(ex) =>
            onError(ex)
        }
    })

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribe(nextFn: T => Unit, errorFn: Throwable => Unit): Unit =
    subscribe(nextFn, errorFn, () => ())

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribe(nextFn: T => Unit): Unit =
    subscribe(nextFn, error => throw new IllegalStateException("onError not implemented"), () => ())

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribe(): Unit =
    subscribe(elem => (), error => throw new IllegalStateException("onError not implemented"), () => ())


  /**
   * Returns an Observable that applies the given function to each item emitted by an
   * Observable and emits the result.
   *
   * @param f a function to apply to each item emitted by the Observable
   * @return an Observable that emits the items from the source Observable, transformed by the given function
   */
  def map[U](f: T => U): Observable[U] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) =
          try observer.onNext(f(elem)) catch {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            case NonFatal(ex) =>
              observer.onError(ex)
          }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted()
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
      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          try {
            if (p(elem))
              observer.onNext(elem)
            else
              Continue
          }
          catch {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            case NonFatal(ex) =>
              observer.onError(ex)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted()
      })
    }

  def foreach(cb: T => Unit): Unit =
    subscribe(new Observer[T] {
      def onNext(elem: T) =
        try { cb(elem); Continue } catch {
          case NonFatal(ex) =>
            onError(ex)
        }

      def onCompleted() = Done
      def onError(ex: Throwable) = {
        scheduler.reportFailure(ex)
        Done
      }
    })

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
    map(f).merge

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
   * The difference between [[concat]] and [[merge]] is that `concat` cares about ordering of
   * emitted items (e.g. all items emitted by the first observable in the sequence will come before
   * the elements emitted by the second observable), whereas `merge` doesn't care about that
   * (elements get emitted as they come). Because of back-pressure applied to observables,
   * [[concat]] is safe to use in all contexts, whereas [[merge]] requires buffering.
   *
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def concat[U](implicit ev: T <:< Observable[U]): Observable[U] =
    Observable.create { observerU =>
      // we need to do ref-counting for triggering `EOF` on our observeU
      // when all the children threads have ended
      val finalCompletedPromise = Promise[Done]()
      val refCounter = RefCountCancelable {
        finalCompletedPromise.completeWith(observerU.onCompleted())
      }

      subscribe(new Observer[T] {
        def onNext(childObservable: T) = {
          val upstreamPromise = Promise[Ack]()

          val refID = refCounter.acquire()
          childObservable.subscribe(new Observer[U] {
            def onNext(elem: U) =
              observerU.onNext(elem)

            def onError(ex: Throwable) = {
              // error happened, so signaling both the main thread that it should stop
              // and the downstream consumer of the error
              val f = observerU.onError(ex)
              f.unsafeOnComplete { case result => upstreamPromise.complete(result) }
              f
            }

            def onCompleted() = Future {
              // NOTE: we aren't sending this onCompleted signal downstream to our observerU
              // instead this will eventually send the EOF downstream (reference counting FTW)
              refID.cancel()
              // end of child observable, so signal main thread that it should continue
              upstreamPromise.success(Continue)
              Done
            }
          })

          upstreamPromise.future
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          observerU.onError(ex)
        }

        def onCompleted() = {
          // initiating the `observeU(EOF)` process by counting down on the remaining children
          refCounter.cancel()
          finalCompletedPromise.future
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
   * @return an Observable that emits items that are the result of flattening the items emitted
   *         by the Observables emitted by `this`
   */
  def merge[U](implicit ev: T <:< Observable[U]): Observable[U] = {
    Observable.create { observerB =>
      val ackBuffer = new AckBuffer

      // we need to do ref-counting for triggering `onCompleted` on our subscriber
      // when all the children threads have ended
      val refCounter = RefCountCancelable {
        ackBuffer.scheduleDone(observerB.onCompleted())
      }

      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          // reference that gets released when the child observer is completed
          val refID = refCounter.acquire()

          elem.subscribe(new Observer[U] {
            def onNext(elem: U) =
              ackBuffer.scheduleNext {
                observerB.onNext(elem)
              }

            def onError(ex: Throwable) = {
              // onError, cancel everything
              ackBuffer.scheduleDone(observerB.onError(ex))
            }

            def onCompleted() = {
              // do resource release, otherwise we can end up with a memory leak
              refID.cancel()
              Done
            }
          })

          ackBuffer.scheduleNext(Continue)
        }

        def onError(ex: Throwable) =
          ackBuffer.scheduleDone(observerB.onError(ex))

        def onCompleted() = {
          // triggers observer.onCompleted() when all Observables created have been finished
          // basically when the main thread is completed, it waits to stream onCompleted
          // until all children have been onCompleted too - only after that `subscriber.onCompleted` gets triggered
          // (see `RefCountCancelable` for details on how it works)
          refCounter.cancel()
          Done
        }
      })
    }
  }

  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  def take(n: Long): Observable[T] =
    Observable.create { observer =>
      val counterRef = padded.Atomic(0L)

      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          // short-circuit for not endlessly incrementing that number
          if (counterRef.get < n) {
            // this increment needs to be synchronized - a well behaved producer
            // does back-pressure by means of the acknowledgement that the observer
            // returns, however we can still have visibility problems
            val counter = counterRef.incrementAndGet()

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              observer.onNext(elem)
            }
            else if (counter == n) {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              observer.onNext(elem).unsafeFlatMap { _ =>
                observer.onCompleted()
              }
            }
            else {
              // we already emitted the maximum number of events, so signal upstream
              // to the producer that it should stop sending events
              successful(Done)
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            successful(Done)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted()
      })
    }

  /**
   * Drops the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to drop
   *  @return    a new Observable that drops the first ''n'' elements
   *             emitted by the source
   */
  def drop(n: Long): Observable[T] =
    Observable.create { observer =>
      val count = padded.Atomic(0L)

      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          if (count.get < n && count.getAndIncrement() < n)
            Continue
          else
            observer.onNext(elem)
        }

        def onCompleted() =
          observer.onCompleted()

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
      subscribe(new Observer[T] {
        @volatile var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            try {
              if (p(elem))
                observer.onNext(elem)
              else {
                shouldContinue = false
                observer.onCompleted()
              }
            }
            catch {
              case NonFatal(ex) =>
                shouldContinue = false
                observer.onError(ex)
            }
          }
          else
            Done
        }

        def onCompleted() =
          observer.onCompleted()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Takes longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits those elements.
   */
  def takeWhile(isRefTrue: Atomic[Boolean]): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        @volatile var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            try {
              if (isRefTrue.get)
                observer.onNext(elem)
              else {
                shouldContinue = false
                observer.onCompleted()
              }
            }
            catch {
              case NonFatal(ex) =>
                shouldContinue = false
                observer.onError(ex)
            }
          }
          else
            Done
        }

        def onCompleted() =
          observer.onCompleted()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Drops the longest prefix of elements that satisfy the given predicate
   * and returns a new Observable that emits the rest.
   */
  def dropWhile(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        @volatile var shouldDrop = true

        def onNext(elem: T) = {
          if (shouldDrop) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isInvalid = p(elem)
              streamError = false
              if (isInvalid)
                Continue
              else {
                shouldDrop = false
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) onError(ex) else Future.failed(ex)
            }
          }
          else
            observer.onNext(elem)
        }

        def onCompleted() =
          observer.onCompleted()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Applies a binary operator to a start value and all elements of this Observable,
   * going left to right and returns a new Observable that emits only one item
   * before `onCompleted`.
   */
  def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R] =
    Observable.create { observer =>
      val state = padded.Atomic(initial)

      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            state.transformAndGet(s => op(s, elem))
            Continue
          }
          catch {
            case NonFatal(ex) => onError(ex)
          }
        }

        def onCompleted() =
          observer.onNext(state.get).unsafeFlatMap { _ =>
            observer.onCompleted()
          }

        def onError(ex: Throwable) =
          observer.onError(ex)
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
      val state = padded.Atomic(initial)

      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            val result = state.transformAndGet(s => op(s, elem))
            streamError = false
            observer.onNext(result)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) onError(ex) else Future.failed(ex)
          }
        }

        def onCompleted() =
          observer.onCompleted()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Executes the given callback when the stream has ended on `onCompleted`
   *
   * NOTE: protect the callback such that it doesn't throw exceptions, because
   * it gets executed when `cancel()` happens and by definition the error cannot
   * be streamed with `onError()` and so the behavior is left as undefined, possibly
   * crashing the application or worse - leading to non-deterministic behavior.
   *
   * @param cb the callback to execute when the subscription is canceled
   */
  def doOnComplete(cb: => Unit): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) =
          observer.onNext(elem)

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() = {
          var streamError = true
          try {
            cb
            streamError = false
            observer.onCompleted()
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) observer.onError(ex) else Future.failed(ex)
          }
        }
      })
    }
  
  def doOnTerminated(cb: => Unit): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          val result = observer.onNext(elem)
          result.unsafeOnSuccess {
            case Done => cb
            case _ => // nothing
          }
          result
        }

        def onError(ex: Throwable): Future[Done] = {
          val result = observer.onError(ex)
          result.unsafeOnSuccess { case _ => cb }
          result
        }

        def onCompleted(): Future[Done] = {
          val result = observer.onCompleted()
          result.onSuccess { case _ => cb }
          result
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
      subscribe(new Observer[T] {
        def onError(ex: Throwable) = observer.onError(ex)
        def onCompleted() = observer.onCompleted()

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
              if (streamError) onError(ex) else Future.failed(ex)
          }
        }
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
   * Returns the first generated result as a Future and then cancels
   * the subscription.
   */
  def asFuture: Future[Option[T]] = {
    val promise = Promise[Option[T]]()

    head.subscribe(new Observer[T] {
      def onNext(elem: T) = {
        promise.trySuccess(Some(elem))
        successful(Done)
      }

      def onCompleted() = {
        promise.trySuccess(None)
        Done
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
        Done
      }
    })

    promise.future
  }

  /**
   * Concatenates the source Observable with the other Observable, as specified.
   */
  def ++[U >: T](other: => Observable[U]): Observable[U] =
    Observable.fromSequence(Seq(this, other)).flatten

  /**
   * Only emits the first element emitted by the source observable, after which it's completed immediately.
   */
  def head: Observable[T] = take(1)

  /**
   * Drops the first element of the source observable, emitting the rest.
   */
  def tail: Observable[T] = drop(1)

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
      val lock = new AnyRef

      val queueA = mutable.Queue.empty[(Promise[U], Promise[Ack])]
      val queueB = mutable.Queue.empty[(U, Promise[Ack])]

      val completedPromise = Promise[Done]()
      var isCompleted = false

      def _onError(ex: Throwable) = lock.synchronized {
        if (!isCompleted) {
          isCompleted = true
          queueA.clear()
          queueB.clear()
          observerOfPairs.onError(ex)
        }
        else
          Done
      }

      subscribe(new Observer[T] {
        def onNext(a: T): Future[Ack] =
          lock.synchronized {
            if (queueB.isEmpty) {
              val resp = Promise[Ack]()
              val promiseForB = Promise[U]()
              queueA.enqueue((promiseForB, resp))

              val f = promiseForB.future.flatMap(b => observerOfPairs.onNext((a, b)))
              resp.completeWith(f)
              f
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

        def onCompleted() = lock.synchronized {
          if (!isCompleted && queueA.isEmpty) {
            isCompleted = true
            queueA.clear()
            queueB.clear()
            completedPromise.completeWith(observerOfPairs.onCompleted())
          }

          completedPromise.future
        }
      })

      other.subscribe(new Observer[U] {
        def onNext(b: U): Future[Ack] =
          lock.synchronized {
            if (queueA.nonEmpty) {
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

        def onCompleted() = lock.synchronized {
          if (!isCompleted && queueB.isEmpty) {
            isCompleted = true
            queueA.clear()
            queueB.clear()
            completedPromise.completeWith(observerOfPairs.onCompleted())
          }

          completedPromise.future
        }
      })
    }

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for listening to the emitted items.
   */
  def observeOn(s: Scheduler): Observable[T] = {
    implicit val scheduler = s

    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          val p = Promise[Ack]()
          scheduler.execute(new Runnable {
            def run(): Unit =
              p.completeWith(observer.onNext(elem))
          })
          p.future
        }

        def onError(ex: Throwable): Future[Done] = {
          val p = Promise[Done]()
          scheduler.execute(new Runnable {
            def run(): Unit =
              p.completeWith(observer.onError(ex))
          })
          p.future
        }

        def onCompleted(): Future[Done] = {
          val p = Promise[Done]()
          scheduler.execute(new Runnable {
            def run(): Unit =
              p.completeWith(observer.onCompleted())
          })
          p.future
        }
      })
    }
  }

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for initiating the subscription.
   */
  def subscribeOn(s: Scheduler): Observable[T] = {
    implicit val scheduler = s
    Observable.create(o => s.scheduleOnce(subscribe(o)))
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
      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] =
          observer.onNext(OnNext(elem))

        def onError(ex: Throwable): Future[Done] =
          observer.onNext(OnError(ex)).unsafeFlatMap {
            case Done => Done
            case Continue => observer.onCompleted()
          }

        def onCompleted(): Future[Done] =
          observer.onNext(OnComplete).unsafeFlatMap {
            case Done => Done
            case Continue => observer.onCompleted()
          }
      })
    }

  /**
   * Utility that can be used for debugging purposes.
   */
  def dump(prefix: String): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        private[this] val pos = padded.Atomic(0)

        def onNext(elem: T): Future[Ack] = {
          val pos = this.pos.getAndIncrement(2)

          println(s"$pos: $prefix-->$elem")
          val r = observer.onNext(elem)
          r.unsafeOnSuccess {
            case Continue =>
              println(s"${pos+1}: $prefix-->$elem-->Continue")
            case Done =>
              println(s"${pos+1}: $prefix-->$elem-->Done")
          }
          r
        }

        def onError(ex: Throwable): Future[Done] = {
          println(s"${pos.getAndIncrement()}: $prefix-->$ex")
          observer.onError(ex)
        }

        def onCompleted(): Future[Done] = {
          println(s"${pos.getAndIncrement()}: $prefix completed")
          observer.onCompleted()
        }
      })
    }

  /**
   * Converts this observable into a multicast observable, useful for turning a cold observable into
   * a hot one (i.e. whose source is shared by all observers).
   */
  def multicast[U >: T](subject: Subject[U] = PublishSubject[U]()): ConnectableObservable[U] =
    new ConnectableObservable[U] {
      private[this] val notCanceled = Atomic(true)
      val scheduler = self.scheduler

      private[this] val cancelAction =
        BooleanCancelable { notCanceled set false }
      private[this] val notConnected =
        Cancelable { self.takeWhile(notCanceled).subscribe(subject) }

      def connect() = {
        notConnected.cancel()
        cancelAction
      }

      def subscribe(observer: Observer[U]): Unit = {
        subject.subscribe(observer)
      }
    }

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
}

object Observable {
  /**
   * Observable constructor. To be used for implementing new Observables and operators.
   */
  def create[T](f: Observer[T] => Unit)(implicit scheduler: Scheduler): Observable[T] = {
    val s = scheduler
    new Observable[T] {
      val scheduler = s
      def subscribe(observer: Observer[T]): Unit =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
        }
    }
  }

  def empty[A](implicit scheduler: Scheduler): Observable[A] =
    Observable.create { observer =>
      observer.onCompleted()
    }

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A)(implicit scheduler: Scheduler): Observable[A] = {
    Observable.create { observer =>
      observer.onNext(elem).onSuccess {
        case Continue =>
          observer.onCompleted()
        case _ =>
          // nothing
      }
    }
  }

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable)(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { observer =>
      observer.onError(ex)
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { _ => () }

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param period the delay between two emitted events
   * @param scheduler the execution context in which `onNext` will get called
   */
  def interval(period: FiniteDuration)(implicit scheduler: Scheduler): Observable[Long] =
    interval(period, period)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param initialDelay the initial delay to wait before the first emitted number
   * @param period the delay between two subsequent events
   * @param scheduler the execution context in which `onNext` will get called
   */
  def interval(initialDelay: FiniteDuration, period: FiniteDuration)(implicit scheduler: Scheduler): Observable[Long] = {
    Observable.create { observer =>
      val counter = padded.Atomic(0)

      scheduler.scheduleRecursive(initialDelay, period, { reschedule =>
        observer.onNext(counter.incrementAndGet()) foreach {
          case Continue =>
            reschedule()
          case Done =>
            // do nothing else
        }
      })
    }
  }

  /**
   * Creates an Observable that continuously emits the given ''item''
   */
  def continuous[T](elem: T)(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      def loop(elem: T): Unit = {
        scheduler.execute(new Runnable {
          def run(): Unit =
            observer.onNext(elem).onSuccess {
              case Done => // do nothing
              case Continue =>
                loop(elem)
            }
        })
      }

      loop(elem)
    }

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromSequence[T](seq: Seq[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      def startFeedLoop(seq: Seq[T]): Unit =
        scheduler.execute(new Runnable {
          private[this] var streamError = true

          @tailrec
          def loop(seq: Seq[T]): Unit = {
            if (seq.nonEmpty) {
              val elem = seq.head
              val tail = seq.tail
              streamError = false

              val ack = observer.onNext(elem)
              if (ack ne Continue) {
                if (ack ne Done)
                  ack.onSuccess {
                    case Continue =>
                      startFeedLoop(tail)
                    case Done =>
                      // Do nothing else
                  }
              }
              else
                loop(tail)
            }
            else {
              streamError = false
              observer.onCompleted()
            }
          }

          def run(): Unit =
            try {
              streamError = true
              loop(seq)
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) observer.onError(ex) else throw ex
            }
        })

      startFeedLoop(seq)
    }

  def fromIterable[T](iterable: Iterable[T])(implicit scheduler: Scheduler): Observable[T] =
    fromIterable(iterable.asJava)

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromIterable[T](iterable: java.lang.Iterable[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      def startFeedLoop(iterator: java.util.Iterator[T]): Unit =
        scheduler.execute(new Runnable {
          def run(): Unit = synchronized {
            while (true) {
              var streamError = true
              try {
                if (iterator.hasNext) {
                  val elem = iterator.next()
                  streamError = false

                  val ack = observer.onNext(elem)
                  if (ack ne Continue) {
                    if (ack ne Done)
                      ack.onSuccess {
                        case Continue =>
                          startFeedLoop(iterator)
                        case Done =>
                          // do nothing else
                      }
                    return
                  }
                }
                else {
                  streamError = false
                  observer.onCompleted()
                  return
                }
              }
              catch {
                case NonFatal(ex) =>
                  if (streamError) {
                    observer.onError(ex)
                    return
                  }
                  else
                    throw ex
              }
            }
          }
        })

      val iterator = iterable.iterator()
      startFeedLoop(iterator)
    }

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.fromSequence(sources).flatten

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def merge[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.fromSequence(sources).merge

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def concat[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.fromSequence(sources).concat

  implicit def FutureIsAsyncObservable[T](future: Future[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { observer =>
      future.onComplete {
        case Success(value) =>
          observer.onNext(value).onSuccess {
            case Continue => observer.onCompleted()
          }
        case Failure(ex) =>
          observer.onError(ex)
      }
    }
}
