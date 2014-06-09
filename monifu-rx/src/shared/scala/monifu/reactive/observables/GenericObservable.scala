package monifu.reactive.observables

import language.implicitConversions
import monifu.reactive._
import monifu.concurrent.{Cancelable, Scheduler}
import scala.concurrent.{Promise, Future}
import monifu.reactive.api._
import Ack.{Cancel, Continue}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables._
import scala.util.control.NonFatal
import scala.collection.mutable
import scala.util.{Failure, Success}
import monifu.reactive.subjects.{ReplaySubject, BehaviorSubject, PublishSubject}
import monifu.reactive.api.Notification.{OnComplete, OnNext, OnError}
import monifu.reactive.observers.{BufferedObserver, SafeObserver, ConcurrentObserver}
import monifu.reactive.internals.{MergeBuffer, FutureAckExtensions}
import monifu.reactive.api.BufferPolicy.{Unbounded, BackPressured}
import monifu.concurrent.extensions._


trait GenericObservable[+T] extends Observable[T] { self =>
  final def map[U](f: T => U): Observable[U] =
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

  final def filter(p: T => Boolean): Observable[T] =
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

  final def flatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).flatten

  final def concatMap[U](f: T => Observable[U]): Observable[U] =
    map(f).concat

  final def mergeMap[U](f: T => Observable[U]): Observable[U] =
    map(f).merge

  final def flatten[U](implicit ev: T <:< Observable[U]): Observable[U] = concat

  final def concat[U](implicit ev: T <:< Observable[U]): Observable[U] =
    Observable.create { observerU =>
      unsafeSubscribe(new Observer[T] {
        def onNext(childObservable: T) = {
          val upstreamPromise = Promise[Ack]()

          childObservable.unsafeSubscribe(new Observer[U] {
            def onNext(elem: U) = {
              observerU.onNext(elem)
                .onCancelComplete(upstreamPromise)
            }

            def onError(ex: Throwable) = {
              // error happened, so signaling both the main thread that it should stop
              // and the downstream consumer of the error
              observerU.onError(ex)
              upstreamPromise.success(Cancel)
            }

            def onComplete() = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // instead we are just instructing upstream to send the next observable
              upstreamPromise.success(Continue)
            }
          })

          upstreamPromise.future
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          observerU.onError(ex)
        }

        def onComplete() = {
          // at this point all children observables have ended
          observerU.onComplete()
        }
      })
    }

  final def merge[U](implicit ev: T <:< Observable[U]): Observable[U] = {
    merge(BackPressured(2048))
  }

  final def merge[U](bufferPolicy: BufferPolicy)(implicit ev: T <:< Observable[U]): Observable[U] = {
    val parallelism = math.min(1024, math.max(1, Runtime.getRuntime.availableProcessors()) * 8)
    merge(parallelism, bufferPolicy)
  }

  final def merge[U](parallelism: Int, bufferPolicy: BufferPolicy)(implicit ev: T <:< Observable[U]): Observable[U] = {
    Observable.create { observerB =>
      unsafeSubscribe(new Observer[T] {
        private[this] val buffer: MergeBuffer[U] =
          new MergeBuffer[U](observerB, parallelism, bufferPolicy)

        def onNext(elem: T) = {
          buffer.merge(elem)
        }

        def onError(ex: Throwable) = {
          buffer.onError(ex)
        }

        def onComplete() = {
          buffer.onComplete()
        }
      })
    }
  }

  final def unsafeMerge[U](implicit ev: T <:< Observable[U]): Observable[U] = {
    merge(0, Unbounded)
  }

  final def take(n: Int): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var counter = 0

        def onNext(elem: T) = {
          // short-circuit for not endlessly incrementing that number
          if (counter < n) {
            counter += 1

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              observer.onNext(elem)
            }
            else  {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              observer.onNext(elem).flatMap {
                case Cancel => Cancel
                case Continue =>
                  observer.onComplete()
                  Cancel
              }
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            Cancel
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete() =
          observer.onComplete()
      })
    }

  final def takeRight(n: Int): Observable[T] =
    Observable.create { observer =>
      subscribe(new Observer[T] {
        private[this] val queue = mutable.Queue.empty[T]
        private[this] var queued = 0

        def onNext(elem: T): Future[Ack] = {
          if (queued < n) {
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

  final def drop(n: Int): Observable[T] =
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

  final def takeWhile(p: T => Boolean): Observable[T] =
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
              if (isValid)
                observer.onNext(elem)
              else {
                shouldContinue = false
                observer.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            Cancel
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  final def takeWhile(isRefTrue: Atomic[Boolean]): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val continue = isRefTrue.get
              streamError = false

              if (continue)
                observer.onNext(elem)
              else {
                shouldContinue = false
                observer.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            Cancel
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  final def dropWhile(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        var shouldDrop = true

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

  final def foldLeft[R](initial: R)(op: (R, T) => R): Observable[R] =
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

        def onComplete() =
          observer.onNext(state).onSuccess {
            case Continue => observer.onComplete()
          }

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  final def reduce[U >: T](op: (U, U) => U): Observable[U] =
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

        def onComplete() =
          if (wasApplied)
            observer.onNext(state).onSuccess {
              case Continue => observer.onComplete()
            }
          else
            observer.onComplete()

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  final def scan[R](initial: R)(op: (R, T) => R): Observable[R] =
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

  final def flatScan[R](initial: R)(op: (R, T) => Future[R]): Observable[R] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try op(state, elem).liftTry.flatMap {
            case Success(newState) =>
              // clear happens before relationship between
              // subsequent invocations, so this is thread-safe
              state = newState
              observer.onNext(newState)

            case Failure(ex) =>
              observer.onError(ex)
              Cancel
          }
          catch {
            case NonFatal(ex) =>
              observer.onError(ex)
              Cancel
          }
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  final def scan[U >: T](op: (U, U) => U): Observable[U] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var state: U = _
        private[this] var isFirst = true

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            if (isFirst) {
              state = elem
              isFirst = false
            }
            else
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

  final def doOnComplete(cb: => Unit): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          val f = observer.onNext(elem)
          f.onSuccess { case Cancel => cb }
          f
        }

        def onError(ex: Throwable): Unit =
          observer.onError(ex)

        def onComplete(): Unit = {
          try observer.onComplete() finally {
            try cb catch {
              case NonFatal(ex) =>
                scheduler.reportFailure(ex)
            }
          }
        }
      })
    }

  final def doWork(cb: T => Unit): Observable[T] =
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

  final def doOnStart(cb: T => Unit): Observable[T] =
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

  final def find(p: T => Boolean): Observable[T] =
    filter(p).head

  final def exists(p: T => Boolean): Observable[Boolean] =
    find(p).foldLeft(false)((_, _) => true)

  final def forAll(p: T => Boolean): Observable[Boolean] =
    exists(e => !p(e)).map(r => !r)

  final def complete: Observable[Nothing] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onError(ex: Throwable): Unit =
          observer.onError(ex)
        def onComplete(): Unit =
          observer.onComplete()
      })
    }

  final def error: Observable[Throwable] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onComplete(): Unit =
          observer.onComplete()

        def onError(ex: Throwable): Unit = {
          observer.onNext(ex).onSuccess {
            case Continue => observer.onComplete()
          }
        }
      })
    }

  final def endWithError(error: Throwable): Observable[T] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = observer.onNext(elem)
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onError(error)
      })
    }

  final def +:[U >: T](elems: U*): Observable[U] =
    Observable.from(elems) ++ this

  final def startWith[U >: T](elems: U*): Observable[U] =
    Observable.from(elems) ++ this

  final def :+[U >: T](elems: U*): Observable[U] =
    this ++ Observable.from(elems)

  final def endWith[U >: T](elems: U*): Observable[U] =
    this ++ Observable.from(elems)

  final def ++[U >: T](other: => Observable[U]): Observable[U] =
    Observable.concat(this, other)

  final def head: Observable[T] = take(1)

  final def tail: Observable[T] = drop(1)

  final def last: Observable[T] = takeRight(1)

  final def headOrElse[B >: T](default: => B): Observable[B] =
    head.foldLeft(Option.empty[B])((_, elem) => Some(elem)) map {
      case Some(elem) => elem
      case None => default
    }

  final def firstOrElse[U >: T](default: => U): Observable[U] =
    headOrElse(default)

  final def zip[U](other: Observable[U]): Observable[(T, U)] =
    Observable.create { observerOfPairs =>
      // using mutability, receiving data from 2 producers, so must synchronize
      val lock = new AnyRef
      val queueA = mutable.Queue.empty[(Promise[U], Promise[Ack])]
      val queueB = mutable.Queue.empty[(U, Promise[Ack])]
      var isCompleted = false

      def _onError(ex: Throwable) = lock.synchronized {
        if (!isCompleted) {
          isCompleted = true
          queueA.clear()
          queueB.clear()
          observerOfPairs.onError(ex)
        }
        else
          Cancel
      }

      unsafeSubscribe(new Observer[T] {
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

        def onComplete() = lock.synchronized {
          if (!isCompleted && queueA.isEmpty) {
            isCompleted = true
            queueA.clear()
            queueB.clear()
            observerOfPairs.onComplete()
          }
        }
      })

      other.unsafeSubscribe(new Observer[U] {
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

        def onComplete() = lock.synchronized {
          if (!isCompleted && queueB.isEmpty) {
            isCompleted = true
            queueA.clear()
            queueB.clear()
            observerOfPairs.onComplete()
          }
        }
      })
    }

  final def max[U >: T](implicit ev: Ordering[U]): Observable[U] =
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
          else
            observer.onNext(maxValue).onSuccess {
              case Continue => observer.onComplete()
            }
        }
      })
    }

  final def maxBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
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
          else
            observer.onNext(maxValue).onSuccess {
              case Continue => observer.onComplete()
            }
        }
      })
    }

  final def min[U >: T](implicit ev: Ordering[U]): Observable[T] =
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
          else
            observer.onNext(minValue).onSuccess {
              case Continue => observer.onComplete()
            }
        }
      })
    }

  final def minBy[U](f: T => U)(implicit ev: Ordering[U]): Observable[T] =
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
          else
            observer.onNext(minValue).onSuccess {
              case Continue => observer.onComplete()
            }
        }
      })
    }

  final def sum[U >: T](implicit ev: Numeric[U]): Observable[U] = {
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] var result = ev.zero

        def onNext(elem: T): Future[Ack] = {
          result = ev.plus(result, elem)
          Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete(): Unit = {
          observer.onNext(result).onSuccess {
            case Continue => observer.onComplete()
          }
        }
      })
    }
  }

  final def observeOn(s: Scheduler, bufferPolicy: BufferPolicy = BackPressured(1024)): Observable[T] = {
    implicit val scheduler = s

    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        private[this] val buffer = BufferedObserver(observer, bufferPolicy)(s)

        def onNext(elem: T): Future[Ack] = {
          buffer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          buffer.onError(ex)
        }

        def onComplete(): Unit = {
          buffer.onComplete()
        }
      })
    }
  }

  final def distinct: Observable[T] =
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

  final def distinct[U](fn: T => U): Observable[T] =
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

  final def distinctUntilChanged: Observable[T] =
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

  final def distinctUntilChanged[U](fn: T => U): Observable[T] =
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

  final def subscribeOn(s: Scheduler): Observable[T] = {
    Observable.create(o => s.scheduleOnce(unsafeSubscribe(o)))
  }

  final def materialize: Observable[Notification[T]] =
    Observable.create { observer =>
      unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] =
          observer.onNext(OnNext(elem))

        def onError(ex: Throwable): Unit =
          observer.onNext(OnError(ex)).onSuccess {
            case Continue => observer.onComplete()
          }

        def onComplete(): Unit =
          observer.onNext(OnComplete).onSuccess {
            case Continue => observer.onComplete()
          }
      })
    }

  final def dump(prefix: String): Observable[T] =
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

  final def repeat: Observable[T] = {
    def loop(subject: Subject[T, T], observer: Observer[T]): Unit =
      subject.subscribe(new Observer[T] {
        def onNext(elem: T) = observer.onNext(elem)
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete(): Unit =
          scheduler.scheduleOnce(loop(subject, observer))
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

  final def multicast[R](subject: Subject[T, R]): ConnectableObservable[R] =
    new ConnectableObservable[R] with GenericObservable[R] {
      private[this] val notCanceled = Atomic(true)
      val scheduler = self.scheduler

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

  final def safe: Observable[T] =
    Observable.create { observer => unsafeSubscribe(SafeObserver(observer)) }

  final def concurrent: Observable[T] =
    Observable.create { observer => unsafeSubscribe(ConcurrentObserver(observer)) }

  final def buffered(policy: BufferPolicy = BackPressured(bufferSize = 4096)): Observable[T] =
    Observable.create { observer => unsafeSubscribe(BufferedObserver(observer, policy)) }

  final def publish(): ConnectableObservable[T] =
    multicast(PublishSubject())

  final def behavior[U >: T](initialValue: U): ConnectableObservable[U] =
    multicast(BehaviorSubject(initialValue))

  final def replay(): ConnectableObservable[T] =
    multicast(ReplaySubject())

  final def lift[U](f: Observable[T] => Observable[U]): Observable[U] =
    f(self)
}

object GenericObservable {
  /**
   * Creates a [[GenericObservable]] instance.
   */
  def create[T](f: Observer[T] => Unit)(implicit scheduler: Scheduler): Observable[T] = {
    val s = scheduler
    new GenericObservable[T] {
      val scheduler = s

      override def subscribeFn(observer: Observer[T]): Unit =
        try f(observer) catch {
          case NonFatal(ex) =>
            observer.onError(ex)
        }
    }
  }
}
