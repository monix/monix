package monifu.rx.sync

import monifu.concurrent.cancelables.{CompositeCancelable, RefCountCancelable}
import monifu.rx.sync.observers.{SynchronizedObserver, AnonymousObserver}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.{Scheduler, Cancelable}
import scala.util.control.NonFatal
import monifu.rx.base.{ObservableTypeClass, ObservableLike, Ack}
import Ack.{Continue, Stop}
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration


/**
 * Synchronous implementation of the Observable interface.
 */
trait Observable[+A] extends ObservableLike[A, Observable] {
  type O[-I] = monifu.rx.sync.Observer[I]

  def subscribe(observer: Observer[A]): Cancelable

  def subscribeUnit(nextFn: A => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn))

  def subscribeUnit(nextFn: A => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn))

  def subscribeUnit(nextFn: A => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn, completedFn))

  def map[B](f: A => B): Observable[B] =
    Observable.create(observer =>
      subscribe(new Observer[A] {
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
              Stop
          }
        }
        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() =
          observer.onCompleted()
      }))

  def filter(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribe(new Observer[A] {
      def onNext(elem: A) = {
        // See Section 6.4. - Protect calls to user code from within an operator - in the Rx Design Guidelines
        // Note: onNext must not be protected, as it's on the edge of the monad and protecting it yields weird effects
        var streamError = true
        try {
          val r = p(elem)
          streamError = false
          if (r)
            observer.onNext(elem)
          else
            Continue
        }
        catch {
          case NonFatal(ex) if streamError =>
            observer.onError(ex)
            Stop
        }
      }
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))

  def find(p: A => Boolean): Observable[A] =
    filter(p).head

  def exists(p: A => Boolean): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  def forAll(p: A => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  def flatMap[B](f: A => Observable[B]): Observable[B] =
    map(f).flatten

  def flatten[B](implicit ev: A <:< Observable[B]): Observable[B] =
    Observable.create { observerB =>
    // we need to do ref-counting for triggering `onCompleted` on our subscriber
    // when all the children threads have ended
      val refCounter = RefCountCancelable(observerB.onCompleted())
      val composite = CompositeCancelable()

      composite += subscribe(new Observer[A] {
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
              observerB.onNext(elem)

            def onError(ex: Throwable) =
            // onError, cancel everything
              try observerB.onError(ex) finally composite.cancel()

            def onCompleted() = {
              // do resource release, otherwise we can end up with a memory leak
              composite -= sub
              refID.cancel()
              sub.cancel()
            }
          }

          sub := elem.subscribe(childObserver)
          Continue
        }

        def onError(ex: Throwable) =
          try observerB.onError(ex) finally composite.cancel()

        def onCompleted() = {
          // triggers observer.onCompleted() when all Observables created have been finished
          // basically when the main thread is completed, it waits to stream onCompleted
          // until all children have been onCompleted too - only after that `subscriber.onCompleted` gets triggered
          // (see `RefCountCancelable` for details on how it works)
          refCounter.cancel()
        }
      })

      composite
    }

  def head: Observable[A] = take(1)

  def tail: Observable[A] = drop(1)

  def headOrElse[B >: A](default: => B): Observable[B] =
    head.foldLeft(Option.empty[A])((_, elem) => Some(elem)).map {
      case Some(elem) => elem
      case None => default
    }

  def firstOrElse[B >: A](default: => B): Observable[B] =
    headOrElse(default)

  def take(n: Long): Observable[A] = {
    require(n > 0, "number of elements to take should be strictly positive")

    Observable.create(observer => subscribe(new Observer[A] {
      val count = Atomic(0L)

      @tailrec
      def onNext(elem: A) = {
        val currentCount = count.get

        if (currentCount < n) {
          val newCount = currentCount + 1
          if (!count.compareAndSet(currentCount, newCount))
            onNext(elem)
          else {
            observer.onNext(elem)
            if (newCount == n) {
              observer.onCompleted()
              Stop
            }
            else
              Continue
          }
        }
        else
          Stop
      }

      def onCompleted() =
        observer.onCompleted()

      def onError(ex: Throwable) =
        observer.onError(ex)
    }))
  }

  def drop(n: Long): Observable[A] = {
    require(n > 0, "number of elements to drop should be strictly positive")

    Observable.create(observer => subscribe(new Observer[A] {
      val count = Atomic(0L)

      @tailrec
      def onNext(elem: A) = {
        val currentCount = count.get

        if (currentCount < n) {
          val newCount = currentCount + 1
          if (!count.compareAndSet(currentCount, newCount))
            onNext(elem)
          else
            Continue
        }
        else
          observer.onNext(elem)
      }

      def onCompleted() =
        observer.onCompleted()

      def onError(ex: Throwable) =
        observer.onError(ex)
    }))
  }

  def takeWhile(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribe(new Observer[A] {
      val shouldContinue = Atomic(true)

      def onNext(elem: A) = {
        var streamError = true
        try {
          if (shouldContinue.get) {
            val update = p(elem)
            streamError = false

            if (shouldContinue.compareAndSet(expect=true, update=update) && update) {
              observer.onNext(elem)
              Continue
            }
            else if (!update) {
              observer.onCompleted()
              Stop
            }
            else
              Stop
          }
          else
            Stop
        }
        catch {
          case NonFatal(ex) if streamError =>
            observer.onError(ex)
            Stop
        }
      }

      def onCompleted() =
        observer.onCompleted()

      def onError(ex: Throwable) =
        observer.onError(ex)
    }))

  def dropWhile(p: A => Boolean): Observable[A] =
    Observable.create(observer => subscribe(new Observer[A] {
      val shouldDropRef = Atomic(true)

      @tailrec
      def onNext(elem: A) =
        if (!shouldDropRef.get)
          observer.onNext(elem)
        else {
          val shouldDrop = p(elem)
          if (!shouldDropRef.compareAndSet(expect=true, update=shouldDrop) || !shouldDrop)
            onNext(elem)
          else
            Continue
        }

      def onCompleted() =
        observer.onCompleted()

      def onError(ex: Throwable) =
        observer.onError(ex)
    }))

  def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
    Observable.create { observer =>
      val state = Atomic(initial)

      subscribe(new Observer[A] {
        def onNext(elem: A) =
          try {
            state.transformAndGet(s => f(s, elem))
            Continue
          }
          catch {
            case NonFatal(ex) =>
              observer.onError(ex)
              Stop
          }

        def onCompleted() = {
          observer.onNext(state.get)
          observer.onCompleted()
        }

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  def ++[B >: A](other: => Observable[B]): Observable[B] =
    Observable.create[B](observer => subscribe(
      SynchronizedObserver(new Observer[A] {
        def onNext(elem: A) = observer.onNext(elem)

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() = {
          other.subscribe(observer)
          ()
        }
      })))

  def doOnCompleted(cb: => Unit): Observable[A] =
    Observable.create { observer =>
      subscribe(new Observer[A] {
        def onNext(elem: A) =
          observer.onNext(elem)

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() = {
          observer.onCompleted()
          cb
        }
      })
    }

  def doWork(cb: A => Unit): Observable[A] =
    Observable.create(observer => subscribe(new Observer[A] {
      def onNext(elem: A) = {
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
            Stop
        }
      }

      def onError(ex: Throwable) =
        observer.onError(ex)

      def onCompleted() =
        observer.onCompleted()
    }))

  def zip[B](other: Observable[B]): Observable[(A,B)] =
    Observable.create { observer =>
      val composite = CompositeCancelable()
      val lock = new AnyRef
      val queueA = mutable.Queue.empty[A]
      val queueB = mutable.Queue.empty[B]
      var aIsDone = false
      var bIsDone = false

      def _onError(ex: Throwable) =
        lock.synchronized {
          aIsDone = true
          bIsDone = true
          queueA.clear()
          queueB.clear()
          observer.onError(ex)
        }

      composite += subscribe(new Observer[A] {
        def onNext(elem: A) =
          lock.synchronized {
            if (!aIsDone)
              if (queueB.nonEmpty) {
                val b = queueB.dequeue()
                observer.onNext((elem, b))
              }
              else if (bIsDone) {
                onCompleted()
                Stop
              }
              else {
                queueA.enqueue(elem)
                Continue
              }
            else
              Stop
          }

        def onCompleted() =
          lock.synchronized {
            if (!aIsDone) {
              aIsDone = true
              if (queueA.isEmpty || bIsDone) {
                queueA.clear()
                observer.onCompleted()
              }
            }
          }

        def onError(ex: Throwable) =
          _onError(ex)
      })

      composite += other.subscribe(new Observer[B] {
        def onNext(elem: B) =
          lock.synchronized {
            if (!bIsDone)
              if (queueA.nonEmpty) {
                val a = queueA.dequeue()
                observer.onNext((a, elem))
              }
              else if (aIsDone) {
                onCompleted()
                Stop
              }
              else {
                queueB.enqueue(elem)
                Continue
              }
            else
              Stop
          }

        def onCompleted() =
          lock.synchronized {
            if (!bIsDone) {
              bIsDone = true
              if (queueB.isEmpty || aIsDone) {
                queueB.clear()
                observer.onCompleted()
              }
            }
          }

        def onError(ex: Throwable) =
          _onError(ex)
      })

      composite
    }

  def asFuture(implicit ec: concurrent.ExecutionContext): Future[Option[A]] = {
    val promise = Promise[Option[A]]()

    head.subscribe(new Observer[A] {
      def onNext(elem: A) = {
        promise.trySuccess(Some(elem))
        Stop
      }

      def onError(ex: Throwable) = {
        promise.tryFailure(ex)
        ()
      }

      def onCompleted() = {
        promise.trySuccess(None)
        ()
      }
    })

    promise.future
  }

  def safe: Observable[A] =
    Observable.create(observer => subscribe(SynchronizedObserver(observer)))

  def toAsyncObservable(implicit ec: ExecutionContext): monifu.rx.async.Observable[A] =
    monifu.rx.async.Observable.create { observerA =>
      val ref = Atomic(Future.successful(Continue : Ack))
      val sub = SingleAssignmentCancelable()

      sub := subscribe(SynchronizedObserver(new Observer[A] {
        def onError(ex: Throwable): Unit = {
          val newPromise = Promise[Ack]()
          val oldFuture = ref.getAndSet(newPromise.future)

          newPromise.completeWith(oldFuture flatMap {
            case Continue =>
              sub.cancel()
              observerA.onError(ex).map(_ => Stop)
            case Stop =>
              sub.cancel()
              Future.successful(Stop)
          })
        }

        def onCompleted(): Unit = {
          val newPromise = Promise[Ack]()
          val oldFuture = ref.getAndSet(newPromise.future)

          newPromise.completeWith(oldFuture flatMap {
            case Continue =>
              sub.cancel()
              observerA.onCompleted().map(_ => Stop)
            case Stop =>
              sub.cancel()
              Future.successful(Stop)
          })
        }

        def onNext(elem: A): Ack = {
          val newPromise = Promise[Ack]()
          val oldFuture = ref.getAndSet(newPromise.future)

          newPromise.completeWith(oldFuture flatMap {
            case Continue =>
              observerA.onNext(elem)
            case Stop =>
              sub.cancel()
              Future.successful(Stop)
          })

          Continue
        }
      }))

      sub
    }
}

object Observable extends ObservableTypeClass[Observable] {
  implicit def Builder = this
  type O[-I] = Observer[I]

  /**
   * Observable constructor. To be used for implementing new Observables and operators.
   */
  def create[A](f: Observer[A] => Cancelable): Observable[A] =
    new Observable[A] {
      def subscribe(observer: Observer[A]) =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
            Cancelable.empty
        }
    }

  def empty[A]: Observable[A] =
    create { observer =>
      observer.onCompleted()
      Cancelable.empty
    }

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): Observable[A] =
    create { observer =>
      observer.onNext(elem)
      observer.onCompleted()
      Cancelable.empty
    }

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): Observable[Nothing] =
    create { observer =>
      observer.onError(ex)
      Cancelable.empty
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never: Observable[Nothing] =
    create { _ => Cancelable() }

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](sequence: TraversableOnce[T]): Observable[T] =
    create[T] { observer =>
      var alreadyStopped = false

      Try(sequence.toIterator) match {
        case Success(iterator) =>
          var shouldContinue = true

          while (shouldContinue) {
            var streamError = true
            try {
              if (iterator.hasNext) {
                val next = iterator.next()
                streamError = false
                alreadyStopped = observer.onNext(next) == Stop
                shouldContinue = !alreadyStopped
              }
              else
                shouldContinue = false
            }
            catch {
              case NonFatal(ex) if streamError =>
                observer.onError(ex)
                shouldContinue = false
            }
          }

        case Failure(ex) =>
          observer.onError(ex)
      }

      if (!alreadyStopped) observer.onCompleted()
      Cancelable.empty
    }

  /**
   * Merges the given list of ''observables'' into a single observable.
   *
   * NOTE: the result should be the same as [[monifu.rx.sync.Observable.concat concat]] and in
   *       the asynchronous version it always is.
   */
  def flatten[T](sources: Observable[T]*): Observable[T] =
    Observable.fromTraversable(sources).flatten

  /**
   * Concatenates the given list of ''observables''.
   */
  def concat[T](sources: Observable[T]*): Observable[T] =
    if (sources.isEmpty)
      empty
    else
      sources.tail.foldLeft(sources.head)((acc, elem) => acc ++ elem)

  def interval(initialDelay: FiniteDuration, period: FiniteDuration, s: Scheduler): Observable[Long] = {
    Observable.create { observer =>
      val counter = Atomic(0)
      val sub = SingleAssignmentCancelable()

      sub := s.scheduleRecursive(initialDelay, period, { reschedule =>
        observer.onNext(counter.incrementAndGet()) match {
          case Continue =>
            reschedule()
          case Stop =>
            sub.cancel()
        }
      })

      sub
    }
  }

}