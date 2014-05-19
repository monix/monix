package monifu.rx

import language.implicitConversions
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.Future.successful
import monifu.rx.api._
import Ack.{Done, Continue}
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.cancelables.{BooleanCancelable, SingleAssignmentCancelable, RefCountCancelable, CompositeCancelable}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.collection.mutable
import scala.util.{Try, Failure, Success}


/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] {
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
  def subscribe(observer: Observer[T]): Cancelable

  /**
   * Implicit `scala.concurrent.ExecutionContext` under which our computations will run.
   */
  protected implicit def ec: ExecutionContext

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(new Observer[T] {
      def onNext(elem: T): Future[Ack] =
        Future { nextFn(elem); Continue }

      def onError(ex: Throwable) =
        Future { errorFn(ex); Done }

      def onCompleted() =
        Future { completedFn(); Done }
    })

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribeUnit(nextFn, errorFn, () => ())

  /**
   * Helper to be used by consumers for subscribing to an observable.
   */
  def subscribeUnit(nextFn: T => Unit): Cancelable =
    subscribeUnit(nextFn, error => ec.reportFailure(error), () => ())

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
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          Future(Try(f(elem))) flatMap {
            case Success(u) =>
              observer.onNext(u)
            case Failure(ex) =>
              observer.onError(ex).map(_ => Done)
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
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          Future(Try(p(elem))) flatMap {
            case Success(isValid) =>
              if (isValid)
              // element is valid, so send it downstream
                observer.onNext(elem)
              else
              // not valid, so ignore and signal upstream to send more
                successful(Continue)

            case Failure(ex) =>
              observer.onError(ex).map(_ => Done)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted()
      })
    }

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
  def flatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).flatten

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
  def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] =
    Observable.create { observerU =>
    // aggregate subscription that cancels everything
      val composite = CompositeCancelable()

      // we need to do ref-counting for triggering `EOF` on our observeU
      // when all the children threads have ended
      val finalCompletedPromise = Promise[Done]()
      val refCounter = RefCountCancelable {
        finalCompletedPromise.completeWith(observerU.onCompleted())
      }

      composite += subscribe(new Observer[T] {
        def onNext(childObservable: T) = {
          val upstreamPromise = Promise[Ack]()

          val refID = refCounter.acquireCancelable()
          val sub = SingleAssignmentCancelable()
          composite += sub

          sub := childObservable.subscribe(new Observer[U] {
            def onNext(elem: U) =
              observerU.onNext(elem)

            def onError(ex: Throwable) = {
              // error happened, so signaling both the main thread that it should stop
              // and the downstream consumer of the error
              val f = observerU.onError(ex)
              upstreamPromise.completeWith(f.map(_ => Done))
              f
            }

            def onCompleted() = Future {
              // removing the child subscription as we can have a leak otherwise
              composite -= sub
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

      composite
    }

  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  def take(n: Long): Observable[T] =
    Observable.create { observer =>
      val counterRef = Atomic(0L)

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
              observer.onNext(elem).flatMap { _ =>
                observer.onCompleted().map(_ => Done)
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
      val count = Atomic(0L)

      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          if (count.get < n && count.getAndIncrement() < n)
            successful(Continue)
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

        def onNext(elem: T) =
          if (shouldContinue)
            Future(Try(p(elem))).flatMap {
              case Success(true) =>
                observer.onNext(elem)
              case Success(false) =>
                shouldContinue = false
                successful(Done)
              case Failure(ex) =>
                observer.onError(ex).map(_ => Done)
            }
          else
            successful(Done)

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
          if (shouldDrop)
            Future(Try(p(elem))).flatMap {
              case Success(true) =>
                successful(Continue)
              case Success(false) =>
                shouldDrop = false
                observer.onNext(elem)
              case Failure(ex) =>
                observer.onError(ex).map(_ => Done)
            }
          else
            successful(Done)
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
      val state = Atomic(initial)

      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] =
          Future(Try( state.transformAndGet(s => op(s, elem) ))) flatMap {
            case Success(_) =>
              successful(Continue)
            case Failure(ex) =>
              observer.onError(ex).map(_ => Done)
          }


        def onCompleted() =
          observer.onNext(state.get).flatMap { _ =>
            observer.onCompleted()
          }

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
  def doOnCompleted(cb: => Unit): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) =
          observer.onNext(elem)

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted().map(_ => { cb; Done })
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

        def onNext(elem: T) =
          Future(cb(elem)).map(_ => Continue)
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
  def asFuture(implicit ec: concurrent.ExecutionContext): Future[Option[T]] = {
    val promise = Promise[Option[T]]()

    head.subscribe(new Observer[T] {
      def onNext(elem: T) = {
        promise.trySuccess(Some(elem))
        successful(Done)
      }

      def onCompleted() = {
        promise.trySuccess(None)
        Done.asFuture
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
        Done.asFuture
      }
    })

    promise.future
  }

  /**
   * Concatenates the source Observable with the other Observable, as specified.
   */
  def ++[U >: T](other: => Observable[U]): Observable[U] =
    Observable.fromTraversable(Seq(this, other)).flatten

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
   * Creates a new Observable by applying a function to each item emitted, a function that returns Future
   * results and then flattens that into a new Observable.
   */
  def flatMapFutures[U](f: T => Future[U])(implicit ec: ExecutionContext): Observable[U] =
    Observable.create { observerU =>
      subscribe(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          val promise = Promise[Ack]()
          f(elem).onComplete {
            case Success(u) =>
              promise.completeWith(observerU.onNext(u))
            case Failure(ex) =>
              promise.completeWith(observerU.onError(ex).map(_ => Done))
          }
          promise.future
        }

        def onError(ex: Throwable) =
          observerU.onError(ex)

        def onCompleted() =
          observerU.onCompleted()
      })
    }

  /**
   * Creates a new Observable from this Observable and another given Observable,
   * by emitting elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are ignored.
   */
  def zip[U](other: Observable[U]): Observable[(T, U)] =
    Observable.create { observerOfPairs =>
      val composite = CompositeCancelable()
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
          Done.asFuture
      }

      composite += subscribe(new Observer[T] {
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

      composite += other.subscribe(new Observer[U] {
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

      composite
    }

  /**
   * Returns a new Observable that uses the specified `Scheduler` for listening to the emitted items.
   */
  def listenOn(s: Scheduler): Observable[T] =
    Observable.create(subscribe)(s)

  /**
   * Returns a new Observable that uses the specified `Scheduler` for initiating the subscription.
   */
  def subscribeOn(s: Scheduler): Observable[T] =
    Observable.create { observer =>
      s.schedule(s => subscribe(observer))
    }
}

object Observable {
  /**
   * Observable constructor. To be used for implementing new Observables and operators.
   */
  def create[T](f: Observer[T] => Cancelable)(implicit ctx: ExecutionContext): Observable[T] =
    new Observable[T] {
      protected def ec = ctx
      def subscribe(observer: Observer[T]): Cancelable =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
            Cancelable.empty
        }
    }

  def empty[A](implicit ec: ExecutionContext): Observable[A] =
    Observable.create { observer =>
      observer.onCompleted()
      Cancelable.empty
    }

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A)(implicit ec: ExecutionContext): Observable[A] =
    Observable.create { observer =>
      val sub = BooleanCancelable()
      observer.onNext(elem).onSuccess {
        case Continue =>
          if (!sub.isCanceled)
            observer.onCompleted()
        case _ =>
        // nothing
      }
      sub
    }

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable)(implicit ec: ExecutionContext): Observable[Nothing] =
    Observable.create { observer =>
      observer.onError(ex)
      Cancelable.empty
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never(implicit ec: ExecutionContext): Observable[Nothing] =
    Observable.create { _ => Cancelable() }

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param period the delay between two emitted events
   * @param ec the execution context in which `onNext` will get called
   */
  def interval(period: FiniteDuration)(implicit ec: ExecutionContext): Observable[Long] =
    interval(period, Scheduler.fromContext)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param period the delay between two emitted events
   * @param s the scheduler to use for scheduling the next event and for triggering `onNext`
   */
  def interval(period: FiniteDuration, s: Scheduler): Observable[Long] =
    interval(period, period, s)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param initialDelay the initial delay to wait before the first emitted number
   * @param period the delay between two subsequent events
   * @param ec the execution context in which `onNext` will get called
   */
  def interval(initialDelay: FiniteDuration, period: FiniteDuration)(implicit ec: ExecutionContext): Observable[Long] =
    interval(initialDelay, period, Scheduler.fromContext)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param initialDelay the initial delay to wait before the first emitted number
   * @param period the delay between two subsequent events
   * @param s the scheduler to use for scheduling the next event and for triggering `onNext`
   */
  def interval(initialDelay: FiniteDuration, period: FiniteDuration, s: Scheduler): Observable[Long] = {
    implicit val ec = s

    Observable.create { observer =>
      val counter = Atomic(0)
      val sub = SingleAssignmentCancelable()

      sub := s.scheduleRecursive(initialDelay, period, { reschedule =>
        observer.onNext(counter.incrementAndGet()) foreach {
          case Continue =>
            reschedule()
          case Done =>
            sub.cancel()
        }
      })

      sub
    }
  }

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](seq: TraversableOnce[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { observer =>
      def nextInput(iterator: Iterator[T]) =
        Future {
          if (iterator.hasNext)
            Some(iterator.next())
          else
            None
        }

      def startFeedLoop(subscription: BooleanCancelable, iterator: Iterator[T]): Unit =
        if (!subscription.isCanceled)
          nextInput(iterator).onComplete {
            case Success(Some(elem)) =>
              observer.onNext(elem).onSuccess {
                case Continue =>
                  startFeedLoop(subscription, iterator)
                case Done =>
                // do nothing else
              }
            case Success(None) =>
              observer.onCompleted()

            case Failure(ex) =>
              observer.onError(ex)
          }

      val iterator = seq.toIterator
      val subscription = BooleanCancelable()
      startFeedLoop(subscription, iterator)
      subscription
    }

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Observable.fromTraversable(sources).flatten

  implicit def FutureIsAsyncObservable[T](future: Future[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { observer =>
      val sub = BooleanCancelable()
      future.onComplete {
        case Success(value) if !sub.isCanceled =>
          observer.onNext(value).onSuccess {
            case Continue => observer.onCompleted()
          }
        case Failure(ex) if !sub.isCanceled =>
          observer.onError(ex)
        case _ =>
          // do nothing
      }
      sub
    }
}
