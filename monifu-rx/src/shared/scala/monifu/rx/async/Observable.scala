package monifu.rx.async

import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.Future.successful
import monifu.rx.common.Ack
import Ack.{Stop, Continue}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.{SingleAssignmentCancelable, RefCountCancelable, CompositeCancelable}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Try, Failure, Success}
import monifu.rx.common.Ack


trait Observable[+T] {
  def subscribe(observe: Observer[T]): Cancelable

  final def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit)(implicit ec: ExecutionContext): Cancelable =
    subscribe(new Observer[T] {
      def onNext(elem: T): Future[Ack] =
        Future { nextFn(elem); Continue }

      def onError(ex: Throwable): Future[Unit] =
        Future(errorFn(ex))

      def onCompleted(): Future[Unit] =
        Future(onCompleted())
    })

  final def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit)(implicit ec: ExecutionContext): Cancelable =
    subscribeUnit(nextFn, errorFn, () => ())

  final def subscribeUnit(nextFn: T => Unit)(implicit ec: ExecutionContext): Cancelable =
    subscribeUnit(nextFn, error => ec.reportFailure(error), () => ())

  /**
   * Returns an Observable that applies the given function to each item emitted by an
   * Observable and emits the result.
   *
   * @param f a function to apply to each item emitted by the Observable
   * @return an Observable that emits the items from the source Observable, transformed by the given function
   */
  final def map[U](f: T => U)(implicit ec: ExecutionContext): Observable[U] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) =
        // See Section 6.4. in the Rx Design Guidelines:
        // Protect calls to user code from within an operator
          Future(Try(f(elem))) flatMap {
            case Success(u) =>
              observer.onNext(u)
            case Failure(ex) =>
              observer.onError(ex).map(_ => Stop)
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
  final def filter(p: T => Boolean)(implicit ec: ExecutionContext): Observable[T] =
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
              observer.onError(ex).map(_ => Stop)
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
  final def flatMap[U](f: T => Observable[U])(implicit ec: ExecutionContext): Observable[U] =
    Observable.create { observerU =>
    // aggregate subscription that cancels everything
      val composite = CompositeCancelable()

      // we need to do ref-counting for triggering `EOF` on our observeU
      // when all the children threads have ended
      val finalCompletedPromise = Promise[Unit]()
      val refCounter = RefCountCancelable {
        finalCompletedPromise.completeWith(observerU.onCompleted())
      }

      composite += subscribe(new Observer[T] {
        def onNext(seedForU: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          Future(Try(f(seedForU))) flatMap {
            case Success(childObservable) =>
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
                  upstreamPromise.completeWith(f.map(_ => Stop))
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
                }
              })

              upstreamPromise.future

            case Failure(ex) =>
              observerU.onError(ex).map(_ => Stop)
          }
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
   * Creates a new Observable by applying a function to each item emitted, a function that returns Future
   * results and then flattens that into a new Observable.
   */
  final def flatMapFutures[U](f: T => Future[U])(implicit ec: ExecutionContext): Observable[U] =
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
              promise.completeWith(observerU.onError(ex).map(_ => Stop))
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
   * Flattens the sequence of Observables emitted by `this` into one Observable, without any
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
  final def flatten[U](implicit ec: ExecutionContext, ev: T <:< Observable[U]): Observable[U] =
    flatMap(x => x)

  /**
   * Flattens the sequence of Futures emitted by `this` without any transformation.
   *
   * This operation is only available if `this` is of type `Observable[Future[B]]` for some `B`,
   * otherwise you'll get a compilation error.
   *
   * @return an Observable that emits items that are the result of flattening the emitted Futures
   */
  final def flattenFutures[U](implicit ec: ExecutionContext, ev: T <:< Future[U]): Observable[U] =
    flatMapFutures(x => x)


  /**
   * Selects the first ''n'' elements (from the start).
   *
   *  @param  n  the number of elements to take
   *  @return    a new Observable that emits only the first ''n'' elements from the source
   */
  final def take(n: Long)(implicit ec: ExecutionContext): Observable[T] =
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
                observer.onCompleted().map(_ => Stop)
              }
            }
            else {
              // we already emitted the maximum number of events, so signal upstream
              // to the producer that it should stop sending events
              successful(Stop)
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            successful(Stop)
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
  final def drop(n: Long)(implicit ec: ExecutionContext): Observable[T] =
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
  final def takeWhile(p: T => Boolean)(implicit ec: ExecutionContext): Observable[T] =
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
                successful(Stop)
              case Failure(ex) =>
                observer.onError(ex).map(_ => Stop)
            }
          else
            successful(Stop)

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
  final def dropWhile(p: T => Boolean)(implicit ec: ExecutionContext): Observable[T] =
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
                observer.onError(ex).map(_ => Stop)
            }
          else
            successful(Stop)
        }

        def onCompleted() =
          observer.onCompleted()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Applies a binary operator to a start value and all elements of this $coll,
   * going left to right and returns a new Observable that emits only one item
   * before `onCompleted`.
   */
  final def foldLeft[R](initial: R)(op: (R, T) => R)(implicit ec: ExecutionContext): Observable[R] =
    Observable.create { observer =>
      val state = Atomic(initial)

      subscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] =
          Future(Try( state.transformAndGet(s => op(s, elem) ))) flatMap {
            case Success(_) =>
              successful(Continue)
            case Failure(ex) =>
              observer.onError(ex).map(_ => Stop)
          }


        def onCompleted(): Future[Unit] =
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
   * NOTE: make sure that the specified callback doesn't throw errors, because
   * it gets executed when `cancel()` happens and by definition the error cannot
   * be streamed with `onError()` and so the behavior is left as undefined, possibly
   * crashing the application or worse - leading to non-deterministic behavior.
   *
   * @param cb the callback to execute when the subscription is canceled
   */
  final def doOnCompleted(cb: => Unit)(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onNext(elem: T) =
          observer.onNext(elem)

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted().map(_ => cb)
      })
    }

  /**
   * Executes the given callback for each element generated by the source
   * Observable, useful for doing side-effects.
   *
   * @return a new Observable that executes the specified callback for each element
   */
  final def doWork(cb: T => Unit)(implicit ec: ExecutionContext): Observable[T] =
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
  final def find(p: T => Boolean)(implicit ec: ExecutionContext): Observable[T] =
    filter(p).head

  /**
   * Returns an Observable which emits a single value, either true, in case the given predicate holds for at least
   * one item, or false otherwise.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for at least one item
   */
  final def exists(p: T => Boolean)(implicit ec: ExecutionContext): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  /**
   * Returns an Observable that emits a single boolean, either true, in case the given predicate holds for all the items
   * emitted by the source, or false in case at least one item is not verifying the given predicate.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only true or false in case the given predicate holds or not for all the items
   */
  final def forAll(p: T => Boolean)(implicit ec: ExecutionContext): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  /**
   * Returns the first generated result as a Future and then cancels
   * the subscription.
   */
  final def asFuture(implicit ec: ExecutionContext): Future[Option[T]] = {
    val promise = Promise[Option[T]]()

    head.subscribe(new Observer[T] {
      def onNext(elem: T) = {
        promise.trySuccess(Some(elem))
        successful(Stop)
      }

      def onCompleted() = {
        promise.trySuccess(None)
        successful(())
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
        successful(())
      }
    })

    promise.future
  }

  final def ++[U >: T](other: => Observable[U])(implicit ec: ExecutionContext): Observable[U] =
    Observable.fromTraversable(Seq(this, other)).flatten


  final def head(implicit ec: ExecutionContext): Observable[T] = take(1)

  final def tail(implicit ec: ExecutionContext): Observable[T] = drop(1)

  final def headOrElse[B >: T](default: => B)(implicit ec: ExecutionContext): Observable[B] =
    head.foldLeft(Option.empty[B])((_, elem) => Some(elem)) map {
      case Some(elem) => elem
      case None => default
    }

  final def firstOrElse[U >: T](default: => U)(implicit ec: ExecutionContext): Observable[U] =
    headOrElse(default)
}

object Observable {
  def create[T](f: Observer[T] => Cancelable): Observable[T] =
    new Observable[T] {
      def subscribe(observe: Observer[T]): Cancelable =
        f(observe)
    }

  def empty[A]: Observable[A] =
    create { observer =>
      observer.onCompleted()
      Cancelable.alreadyCanceled
    }

  def unit[A](elem: A): Observable[A] =
    create { observer =>
      observer.onNext(elem)
      observer.onCompleted()
      Cancelable.alreadyCanceled
    }

  def error(ex: Throwable): Observable[Nothing] =
    create { observer =>
      observer.onError(ex)
      Cancelable.alreadyCanceled
    }

  def never: Observable[Nothing] =
    create { _ => Cancelable() }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    interval(period, period)

  def interval(initialDelay: FiniteDuration, period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable.create { observer =>
      val counter = Atomic(0)
      val sub = SingleAssignmentCancelable()

      sub := s.scheduleRecursive(initialDelay, period, { reschedule =>
        observer.onNext(counter.incrementAndGet()) foreach {
          case Continue =>
            reschedule()
          case Stop =>
            sub.cancel()
        }
      })

      sub
    }

  def fromTraversable[T](seq: TraversableOnce[T])(implicit ec: ExecutionContext): Observable[T] =
    Observable.create { observer =>
      def nextInput(iterator: Iterator[T]) =
        Future {
          if (iterator.hasNext)
            Some(iterator.next())
          else
            None
        }

      def startFeedLoop(subscription: Cancelable, iterator: Iterator[T]): Unit =
        if (!subscription.isCanceled)
          nextInput(iterator).onComplete {
            case Success(Some(elem)) =>
              observer.onNext(elem).onSuccess {
                case Continue =>
                  startFeedLoop(subscription, iterator)
                case Stop =>
                // do nothing else
              }
            case Success(None) =>
              observer.onCompleted()

            case Failure(ex) =>
              observer.onError(ex)
          }

      val iterator = seq.toIterator
      val subscription = Cancelable()
      startFeedLoop(subscription, iterator)
      subscription
    }
}
