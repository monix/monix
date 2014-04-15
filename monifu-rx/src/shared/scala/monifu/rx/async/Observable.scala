package monifu.rx.async

import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.Future.successful
import monifu.rx.base.{ObservableGen, Ack}
import Ack.{Stop, Continue}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.{SingleAssignmentCancelable, RefCountCancelable, CompositeCancelable}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Try, Failure, Success}


/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] extends ObservableGen[T] {
  type This[+I] = Observable[I]
  type Observer[-I] = monifu.rx.async.Observer[I]

  /**
   * Function that creates the actual subscription when calling `subscribe`,
   * and that starts the stream, being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[monifu.rx.sync.Observer Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Rx grammar.
   *
   * @return a cancelable that can be used to cancel the streaming
   */
  def subscribe(observer: Observer[T]): Cancelable

  /**
   * Implicit `scala.concurrent.ExecutionContext` under which our computations will run.
   */
  protected implicit def ec: ExecutionContext

  final def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(new Observer[T] {
      def onNext(elem: T): Future[Ack] =
        Future { nextFn(elem); Continue }

      def onError(ex: Throwable): Future[Unit] =
        Future(errorFn(ex))

      def onCompleted(): Future[Unit] =
        Future(onCompleted())
    })

  final def subscribeUnit(nextFn: T => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribeUnit(nextFn, errorFn, () => ())

  final def subscribeUnit(nextFn: T => Unit): Cancelable =
    subscribeUnit(nextFn, error => ec.reportFailure(error), () => ())

  final def map[U](f: T => U): Observable[U] =
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

  final def filter(p: T => Boolean): Observable[T] =
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

  final def flatMap[U](f: T => Observable[U]): Observable[U] =
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

  final def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] =
    flatMap(x => x)


  final def take(n: Long): Observable[T] =
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

  final def drop(n: Long): Observable[T] =
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

  final def takeWhile(p: T => Boolean): Observable[T] =
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

  final def dropWhile(p: T => Boolean): Observable[T] =
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

  final def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R] =
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

  final def doOnCompleted(cb: => Unit): Observable[T] =
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

  final def doWork(cb: T => Unit): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        def onError(ex: Throwable) = observer.onError(ex)
        def onCompleted() = observer.onCompleted()

        def onNext(elem: T) =
          Future(cb(elem)).map(_ => Continue)
      })
    }

  final def find(p: T => Boolean): Observable[T] =
    filter(p).head

  final def exists(p: T => Boolean): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  final def forAll(p: T => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  final def asFuture(implicit ec: concurrent.ExecutionContext): Future[Option[T]] = {
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

  final def ++[U >: T](other: => Observable[U]): Observable[U] =
    Observable.fromTraversable(Seq(this, other)).flatten

  final def head: Observable[T] = take(1)

  final def tail: Observable[T] = drop(1)

  final def headOrElse[B >: T](default: => B): Observable[B] =
    head.foldLeft(Option.empty[B])((_, elem) => Some(elem)) map {
      case Some(elem) => elem
      case None => default
    }

  final def firstOrElse[U >: T](default: => U): Observable[U] =
    headOrElse(default)

  /**
   * Creates a new Observable by applying a function to each item emitted, a function that returns Future
   * results and then flattens that into a new Observable.
   */
  final def flatMapFutures[U](f: T => Future[U]): Observable[U] =
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
   * Flattens the sequence of Futures emitted by `this` without any transformation.
   *
   * This operation is only available if `this` is of type `Observable[Future[B]]` for some `B`,
   * otherwise you'll get a compilation error.
   *
   * @return an Observable that emits items that are the result of flattening the emitted Futures
   */
  final def flattenFutures[U](implicit ev: T <:< Future[U]): Observable[U] =
    flatMapFutures(x => x)

  /**
   * Returns a new Observable that uses the specified `ExecutionContext` for listening to the emitted items.
   *
   * Take this example:
   * {{{
   *   implicit def ec = myInitialContext
   *   def ec2 = mySecondContext
   *
   *   val obs = Observable.fromSequence(0 until 1000).map(_ + 1).filter(_ % 2 == 0)
   *     .executeOn(ec2).map(_ + 2).filter(_ % 2 == 4).subscribeUnit(x => println(x))
   * }}}
   *
   * In the above example `myInitialContext` is used for emitting the numbers `fromSequence`, for the first `map`
   * propagation, for the first `filter` propagation, after which the second `map` propagation, the second `filter`
   * propagation and the final `Observer` passed to `subscribeUnit` shall use `mySecondContext`.
   */
  final def executeOn(ctx: ExecutionContext): Observable[T] =
    Observable.create(subscribe)(ctx)
}

object Observable {
  implicit def Builder(implicit ec: ExecutionContext): ObservableBuilder =
    new ObservableBuilder()

  /**
   * Observable constructor. To be used for implementing new Observables and operators.
   */
  def create[T](f: Observer[T] => Cancelable)(implicit ctx: ExecutionContext): Observable[T] =
    new Observable[T] {
      protected def ec = ctx
      def subscribe(observe: Observer[T]): Cancelable =
        f(observe)
    }

  def empty[A](implicit ec: ExecutionContext): Observable[A] =
    Builder(ec).empty

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A)(implicit ec: ExecutionContext): Observable[A] =
    Builder(ec).unit(elem)

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable)(implicit ec: ExecutionContext): Observable[Nothing] =
    Builder(ec).error(ex)

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never(implicit ec: ExecutionContext): Observable[Nothing] =
    Builder(ec).never

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

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](seq: TraversableOnce[T])(implicit ec: ExecutionContext): Observable[T] =
    Builder(ec).fromTraversable(seq)

  /**
   * Merges the given list of ''observables'' into a single observable.
   *
   * NOTE: the result should be the same as [[monifu.rx.async.Observable.concat concat]] and in
   *       the asynchronous version it always is.
   */
  def merge[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Builder(ec).merge(sources:_*)

  /**
   * Concatenates the given list of ''observables''.
   */
  def concat[T](sources: Observable[T]*)(implicit ec: ExecutionContext): Observable[T] =
    Builder(ec).concat(sources:_*)
}
