package monifu.rx

import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import monifu.concurrent.cancelables._
import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.rx.observers._
import scala.util.control.NonFatal
import scala.concurrent.duration.FiniteDuration


trait Observable[+A]  {
  /**
   * Function that creates the actual subscription when calling `subscribe`,
   * and that starts the stream, being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param subscriber is both an `Observer[A]` that you can use to push items
   *                   on the stream, it's also a `CompositeCancelable` to which you
   *                   can add whatever things you want to get canceled when `cancel()`
   *                   is invoked and that can also be used to check if the subscription
   *                   has been canceled from the outside - just return it as the result
   *                   of this method, after all cancelables you want were added to it
   *
   * @return a cancelable that can be used to cancel the streaming - in general it should be
   *         the subscriber given as parameter
   */
  def unsafeSubscribe(subscriber: Subscriber[A]): Cancelable

  final def subscribe(observer: Observer[A]): Cancelable = {
    val sub = CompositeCancelable()
    val subscriber = Subscriber(AutoDetachObserver(observer, sub), sub)
    unsafeSubscribe(subscriber)
  }

  final def subscribe(nextFn: A => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn, completedFn))

  final def map[B](f: A => B): Observable[B] =
    Observable.map(this)(f)

  final def filter(p: A => Boolean): Observable[A] =
    Observable.filter(this)(p)

  final def flatMap[B](f: A => Observable[B]): Observable[B] =
    Observable.create(subscriber => {
      val refCounter = RefCountCancelable(subscriber.onCompleted())

      unsafeSubscribe(subscriber.map(observer => new Observer[A] {
        def onNext(elem: A) = {
          val refID = refCounter.acquireCancelable()
          val sub = SingleAssignmentCancelable()

          val childObserver = new Observer[B] {
            def onNext(elem: B) =
              observer.onNext(elem)

            def onError(ex: Throwable) =
              // onError, cancel everything
              try observer.onError(ex) finally subscriber.cancel()

            def onCompleted() = {
              // do resource release
              subscriber -= sub
              refID.cancel()
              sub.cancel()
            }
          }

          subscriber += sub
          sub := f(elem).unsafeSubscribe(Subscriber(childObserver))
        }

        def onError(ex: Throwable) =
          try observer.onError(ex) finally subscriber.cancel()

        def onCompleted() =
          refCounter.cancel() // triggers observer.onCompleted() when all Observables created have been finished
      }))

      subscriber
    })

  final def subscribeOn(s: Scheduler): Observable[A] =
    Observable.create { subscriber =>
      subscriber += s.schedule(_ => unsafeSubscribe(subscriber))
      subscriber
    }

  final def head: Observable[A] = take(1)
  final def tail: Observable[A] = drop(1)

  final def take(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to take should be strictly positive")

    Observable.create(s => unsafeSubscribe(s.map(observer => new Observer[A] {
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
    })))
  }

  final def drop(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to drop should be strictly positive")

    Observable.create(s => unsafeSubscribe(s.map(observer => new Observer[A] {
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
    })))
  }

  final def takeWhile(p: A => Boolean): Observable[A] =
    Observable.create(s => unsafeSubscribe(s.map(observer => new Observer[A] {
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
    })))

  final def dropWhile(p: A => Boolean): Observable[A] =
    Observable.create(s => unsafeSubscribe(s.map(observer => new Observer[A] {
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
    })))

  final def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
    Observable.create { subscriber =>
      val state = Atomic(initial)

      unsafeSubscribe(subscriber.map(observer => new Observer[A] {
        def onNext(elem: A): Unit =
          state.transformAndGet(s => f(s, elem))

        def onCompleted(): Unit = {
          observer.onNext(state.get)
          observer.onCompleted()
        }

        def onError(ex: Throwable): Unit =
          observer.onError(ex)
      }))
    }

  final def ++[B >: A](other: => Observable[B]): Observable[B] =
    Observable.create[B](s => unsafeSubscribe(s.map(observer =>
      SynchronizedObserver(new Observer[A] {
        def onNext(elem: A): Unit = observer.onNext(elem)
        def onError(ex: Throwable): Unit = observer.onError(ex)
        def onCompleted(): Unit = other.unsafeSubscribe(s)
      }))))

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

  final def synchronized: Observable[A] =
    Observable.create(s => unsafeSubscribe(s.map(SynchronizedObserver.apply)))
}

object Observable {
  def create[A](f: Subscriber[A] => Cancelable): Observable[A] =
    new Observable[A] {
      def unsafeSubscribe(subscriber: Subscriber[A]) =
        try f(subscriber) catch {
          case NonFatal(ex) =>
            subscriber.onError(ex)
            subscriber
        }
    }

  def empty[A]: Observable[A] =
    Observable.create { subscriber =>
      if (!subscriber.isCanceled) subscriber.onCompleted()
      subscriber
    }

  def unit[A](elem: A): Observable[A] =
    Observable.create { subscriber =>
      if (!subscriber.isCanceled) {
        subscriber.onNext(elem)
        subscriber.onCompleted()
      }

      subscriber
    }

  def error(ex: Throwable): Observable[Nothing] =
    Observable.create { subscriber =>
      if (!subscriber.isCanceled) subscriber.onError(ex)
      subscriber
    }

  def never: Observable[Nothing] =
    Observable.create { subscriber => subscriber }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable.create { subscriber =>
      val counter = Atomic(0L)
      subscriber += s.scheduleRepeated(period, period, {
        val nr = counter.getAndIncrement()
        if (!subscriber.isCanceled) subscriber.onNext(nr)
      })

      subscriber
    }

  def fromIterable[T](iterable: Iterable[T]): Observable[T] =
    fromSequence(iterable)

  def fromSequence[T](sequence: TraversableOnce[T]): Observable[T] =
    Observable.create[T] { subscriber =>
      if (!subscriber.isCanceled) {
        for (i <- sequence)
          if (!subscriber.isCanceled)
            subscriber.onNext(i)

        if (!subscriber.isCanceled)
          subscriber.onCompleted()
      }

      subscriber
    }

  def map[T, U](source: Observable[T])(f: T => U): Observable[U] =
    Observable.create(s =>
      source.unsafeSubscribe(s.map(observer => new Observer[T] {
        def onNext(elem: T) = {
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
      })))

  def filter[T](source: Observable[T])(p: T => Boolean): Observable[T] =
    Observable.create(s => source.unsafeSubscribe(s.map(observer => new Observer[T] {
      def onNext(elem: T) = {
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
    })))
}
