package monifu.rx

import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import monifu.concurrent.cancelables._
import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.rx.observers._
import scala.util.control.NonFatal
import scala.concurrent.duration.FiniteDuration
import scala.collection.mutable
import monifu.concurrent.locks.NaiveSpinLock


/**
 * The Observable interface that implements the Rx pattern.
 *
 * Observables are characterized by their `subscribeFn` function,
 * that must be overwritten for custom operators or for custom
 * Observable implementations and on top of which everything else
 * is built.
 */
trait Observable[+A]  {
  /**
   * Function that creates the actual subscription when calling `subscribe`,
   * and that starts the stream, being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[monifu.rx.Observer Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Rx grammar.
   *
   * @return a cancelable that can be used to cancel the streaming
   */
  protected def subscribeFn(observer: Observer[A]): Cancelable

  /**
   * Public version of the [[monifu.rx.Observable.subscribeFn subscribeFn]] function.
   *
   * It wraps the given observer into an [[monifu.rx.observers.AutoDetachObserver AutoDetachObserver]]
   * that ensures the subscription is properly canceled either `onCompleted` or `onError`. This function
   * is not used for building custom operators / combinators, because it suffices to wrap only the last
   * observer instance in the chain of observers (i.e. custom operators / combinators rely on `subscribeFn`).
   */
  final def subscribe(observer: Observer[A]): Cancelable = {
    val sub = SingleAssignmentCancelable()
    val subscriber = AutoDetachObserver(observer, sub)
    sub := subscribeFn(subscriber)
    sub
  }

  final def subscribe(nextFn: A => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn, completedFn))

  /**
   * Asynchronously subscribes Observers on the specified `monifu.concurrent.Scheduler`.
   *
   * @param scheduler the `monifu.concurrent.Scheduler` to perform subscription actions on
   * @return the source Observable modified so that its subscriptions happen
   *         on the specified `monifu.concurrent.Scheduler`
   */
  final def subscribeOn(scheduler: Scheduler): Observable[A] =
    Observable.create { observer =>
      scheduler.schedule(_ => subscribeFn(observer))
    }

  /**
   * Returns an Observable that applies the given function to each item emitted by an
   * Observable and emits the result.
   *
   * @param f a function to apply to each item emitted by the Observable
   * @return an Observable that emits the items from the source Observable, transformed by the given function
   */
  def map[B](f: A => B): Observable[B] =
    Observable.create(observer =>
      subscribeFn(new Observer[A] {
        def onNext(elem: A) = {
          // See Section 6.4. - Protect calls to user code from within an operator - in the Rx Design Guidelines
          // Note: onNext must not be protected, as it's on the edge of the monad and protecting it yields weird effects
          var streamError = true
          try {
            val r = f(elem)
            streamError = false
            observer.onNext(r)
          }
          catch {
            case NonFatal(ex) if streamError =>
              observer.onError(ex)
          }
        }
        def onError(ex: Throwable) = observer.onError(ex)
        def onCompleted() = observer.onCompleted()
      }))

  /**
   * Returns an Observable which only emits those items for which a given predicate holds.
   *
   * @param p a function that evaluates the items emitted by the source Observable, returning `true` if they pass the filter
   * @return an Observable that emits only those items in the original Observable for which the filter evaluates as `true`
   */
  def filter(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribeFn(new Observer[A] {
      def onNext(elem: A) = {
        // See Section 6.4. - Protect calls to user code from within an operator - in the Rx Design Guidelines
        // Note: onNext must not be protected, as it's on the edge of the monad and protecting it yields weird effects
        var streamError = true
        try {
          val r = p(elem)
          streamError = false
          if (r) observer.onNext(elem)
        }
        catch {
          case NonFatal(ex) if streamError =>
            observer.onError(ex)
        }
      }
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))


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
  def flatMap[B](f: A => Observable[B]): Observable[B] =
    Observable.create(observer => {
      // we need to do ref-counting for triggering `onCompleted` on our subscriber
      // when all the children threads have ended
      val refCounter = RefCountCancelable(observer.onCompleted())
      val composite = CompositeCancelable()

      composite += subscribeFn(new Observer[A] {
        def onNext(elem: A) = {
          // reference that gets released when the child observer is completed
          val refID = refCounter.acquireCancelable()
          // cancelable reference created for child threads spawned by this flatMap
          // ... is different than `refID` as it serves the purpose of cancelling
          // everything on `cancel()`
          val sub = SingleAssignmentCancelable()
          composite += sub

          val childObserver = new Observer[B] {
            def onNext(elem: B) =
              observer.onNext(elem)

            def onError(ex: Throwable) =
              // onError, cancel everything
              try observer.onError(ex) finally composite.cancel()

            def onCompleted() = {
              // do resource release, otherwise we can end up with a memory leak
              composite -= sub
              refID.cancel()
              sub.cancel()
            }
          }

          // See Section 6.4. - Protect calls to user code from within an operator - in the Rx Design Guidelines
          // Note: onNext must not be protected, as it's on the edge of the monad and protecting it yields weird effects
          var streamError = true
          try {
            val childObs = f(elem)
            streamError = false
            sub := childObs.subscribeFn(childObserver)
          }
          catch {
            case NonFatal(ex) if streamError =>
              observer.onError(ex)
          }
        }

        def onError(ex: Throwable) =
          try observer.onError(ex) finally composite.cancel()

        def onCompleted() = {
          // triggers observer.onCompleted() when all Observables created have been finished
          // basically when the main thread is completed, it waits to stream onCompleted
          // until all children have been onCompleted too - only after that `subscriber.onCompleted` gets triggered
          // (see `RefCountCancelable` for details on how it works)
          refCounter.cancel()
        }
      })

      composite
    })

  final def flatten[B](implicit ev: A <:< Observable[B]): Observable[B] =
    flatMap(x => x)

  final def merge[B](implicit ev: A <:< Observable[B]): Observable[B] =
    flatten

  final def head: Observable[A] = take(1)
  final def tail: Observable[A] = drop(1)

  final def take(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to take should be strictly positive")

    Observable.create(observer => subscribeFn(new Observer[A] {
      val count = Atomic(0)

      @tailrec
      def onNext(elem: A): Unit = {
        val currentCount = count.get

        if (currentCount < nr) {
          val newCount = currentCount + 1
          if (!count.compareAndSet(currentCount, newCount))
            onNext(elem)
          else {
            observer.onNext(elem)
            if (newCount == nr)
              observer.onCompleted()
          }
        }
      }

      def onCompleted(): Unit =
        observer.onCompleted()

      def onError(ex: Throwable): Unit =
        observer.onError(ex)
    }))
  }

  final def drop(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to drop should be strictly positive")

    Observable.create(observer => subscribeFn(new Observer[A] {
      val count = Atomic(0)

      @tailrec
      def onNext(elem: A): Unit = {
        val currentCount = count.get

        if (currentCount < nr) {
          val newCount = currentCount + 1
          if (!count.compareAndSet(currentCount, newCount))
            onNext(elem)
        }
        else
          observer.onNext(elem)
      }

      def onCompleted(): Unit =
        observer.onCompleted()

      def onError(ex: Throwable): Unit =
        observer.onError(ex)
    }))
  }

  final def takeWhile(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribeFn(new Observer[A] {
      val shouldContinue = Atomic(true)

      def onNext(elem: A): Unit =
        if (shouldContinue.get) {
          val update = p(elem)
          if (shouldContinue.compareAndSet(expect=true, update=update) && update)
            observer.onNext(elem)
          else if (!update)
            observer.onCompleted()
        }

      def onCompleted(): Unit =
        observer.onCompleted()

      def onError(ex: Throwable): Unit =
        observer.onError(ex)
    }))

  final def dropWhile(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribeFn(new Observer[A] {
      val shouldDropRef = Atomic(true)

      @tailrec
      def onNext(elem: A): Unit =
        if (!shouldDropRef.get)
          observer.onNext(elem)
        else {
          val shouldDrop = p(elem)
          if (!shouldDropRef.compareAndSet(expect=true, update=shouldDrop) || !shouldDrop)
            onNext(elem)
        }

      def onCompleted(): Unit =
        observer.onCompleted()

      def onError(ex: Throwable): Unit =
        observer.onError(ex)
    }))

  final def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
    Observable.create { observer =>
      val state = Atomic(initial)

      subscribeFn(new Observer[A] {
        def onNext(elem: A): Unit =
          state.transformAndGet(s => f(s, elem))

        def onCompleted(): Unit = {
          observer.onNext(state.get)
          observer.onCompleted()
        }

        def onError(ex: Throwable): Unit =
          observer.onError(ex)
      })
    }

  final def ++[B >: A](other: => Observable[B]): Observable[B] =
    Observable.create[B](observer => subscribeFn(
      SynchronizedObserver(new Observer[A] {
        def onNext(elem: A): Unit = observer.onNext(elem)
        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onCompleted(): Unit = other.subscribeFn(observer)
      })))

  /**
   * Executes the given callback when the subscription is canceled,
   * either manually or because `onCompleted()` was triggered on the observer.
   *
   * NOTE: make sure that the specified callback doesn't throw errors, because
   * it gets executed when `cancel()` happens and by definition the error cannot
   * be streamed with `onError()` and so the behavior is left as undefined, possibly
   * crashing the application or worse - leading to non-deterministic behavior.
   * 
   * @param cb the callback to execute when the subscription is canceled
   */
  final def doOnCancel(cb: => Unit): Observable[A] =
    Observable.create { observer =>
      val composite = CompositeCancelable(Cancelable(cb))
      composite += subscribeFn(observer)
      composite
    }

  /**
   * Executes the given callback for each element generated by the source
   * Observable, useful for doing side-effects.
   * 
   * @return a new Observable that executes the specified callback for each element
   */
  final def doWork(cb: A => Unit): Observable[A] =
    Observable.create(observer => subscribeFn(new Observer[A] {
      def onNext(elem: A): Unit = {
        // See Section 6.4. - Protect calls to user code from within an operator - in the Rx Design Guidelines
        // Note: onNext must not be protected, as it's on the edge of the monad and protecting it yields weird effects
        var streamError = true
        try {
          cb(elem)
          streamError = false
          observer.onNext(elem)
        }
        catch {
          case NonFatal(ex) if streamError =>
            observer.onError(ex)
        }
      }

      def onError(ex: Throwable): Unit = 
        observer.onError(ex)

      def onCompleted(): Unit = 
        observer.onCompleted()
    }))

  final def zip[B](other: Observable[B]): Observable[(A,B)] =
    Observable.create { observer =>
      val composite = CompositeCancelable()
      val lock = NaiveSpinLock()
      val queueA = mutable.Queue.empty[A]
      val queueB = mutable.Queue.empty[B]
      var aIsDone = false
      var bIsDone = false

      def _onError(ex: Throwable) =
        lock.acquire {
          aIsDone = true
          bIsDone = true
          queueA.clear()
          queueB.clear()
          observer.onError(ex)
        }

      composite += subscribeFn(new Observer[A] {
        def onNext(elem: A): Unit =
          lock.acquire {
            if (!aIsDone)
              if (queueB.nonEmpty) {
                val b = queueB.dequeue()
                observer.onNext((elem, b))
              }
              else if (bIsDone)
                onCompleted()
              else
                queueA.enqueue(elem)
          }

        def onCompleted(): Unit =
          lock.acquire {
            if (!aIsDone) {
              aIsDone = true
              if (queueA.isEmpty || bIsDone) {
                queueA.clear()
                observer.onCompleted()
              }
            }
          }

        def onError(ex: Throwable): Unit =
          _onError(ex)
      })

      composite += other.subscribeFn(new Observer[B] {
        def onNext(elem: B): Unit =
          lock.acquire {
            if (!bIsDone)
              if (queueA.nonEmpty) {
                val a = queueA.dequeue()
                observer.onNext((a, elem))
              }
              else if (aIsDone)
                onCompleted()
              else
                queueB.enqueue(elem)
          }

        def onCompleted(): Unit =
          lock.acquire {
            if (!bIsDone) {
              bIsDone = true
              if (queueB.isEmpty || aIsDone) {
                queueB.clear()
                observer.onCompleted()
              }
            }
          }

        def onError(ex: Throwable): Unit =
          _onError(ex)
      })

      composite
    }

  /**
   * Returns the first generated result as a Future and then cancels
   * the subscription.
   */
  final def asFuture(implicit ec: ExecutionContext): Future[Option[A]] = {
    val promise = Promise[Option[A]]()

    head.subscribe(new Observer[A] {
      def onError(ex: Throwable): Unit =
        promise.tryFailure(ex)

      def onNext(elem: A): Unit =
        promise.trySuccess(Some(elem))

      def onCompleted(): Unit =
        promise.trySuccess(None)
    })

    promise.future
  }

  final def safe: Observable[A] =
    Observable.create(observer => subscribeFn(SynchronizedObserver(observer)))
}

object Observable {
  def create[A](f: Observer[A] => Cancelable): Observable[A] =
    new Observable[A] {
      def subscribeFn(observer: Observer[A]) =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
            Cancelable.alreadyCanceled
        }
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
    create { observer =>
      val counter = Atomic(0L)

      s.scheduleRepeated(period, period, {
        val nr = counter.getAndIncrement()
        observer.onNext(nr)
      })
    }

  def fromIterable[T](iterable: Iterable[T], s: Scheduler): Observable[T] =
    fromSequence(iterable, s)

  def fromIterable[T](iterable: Iterable[T]): Observable[T] =
    fromSequence(iterable)

  def fromSequence[T](sequence: TraversableOnce[T], s: Scheduler): Observable[T] =
    create[T] { observer =>
      val sub = SingleAssignmentCancelable()
      sub := s.scheduleOnce {
        if (!sub.isCanceled) {
          for (i <- sequence)
            if (!sub.isCanceled)
              observer.onNext(i)

          if (!sub.isCanceled)
            observer.onCompleted()
        }
      }

      sub
    }

  def fromSequence[T](sequence: TraversableOnce[T]): Observable[T] =
    create[T] { observer =>
      for (i <- sequence) observer.onNext(i)
      observer.onCompleted()
      Cancelable.alreadyCanceled
    }

  def merge[T](sources: Observable[T]*): Observable[T] =
    Observable.fromSequence(sources).flatten
}
