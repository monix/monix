package monifu.rx

import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import monifu.rx.FoldState.{Cont, Emit}
import scala.collection.immutable.Queue
import monifu.concurrent.cancelables._
import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.rx.observers.{SynchronizedObserver, AutoDetachObserver, AnonymousObserver}
import monifu.rx.internal.ObservableUtils


trait Observable[+A]  {
  protected def fn(observer: Observer[A]): Cancelable

  final def subscribe(observer: Observer[A]): Cancelable = {
    val sub = SingleAssignmentCancelable()
    sub := fn(AutoDetachObserver(observer, sub))
    sub
  }

  final def subscribe(nextFn: A => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn))

  final def subscribe(nextFn: A => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Cancelable =
    subscribe(AnonymousObserver(nextFn, errorFn, completedFn))

  final def map[B](f: A => B): Observable[B] =
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A) = observer.onNext(f(elem))
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))

  final def flatMap[B](f: A => Observable[B]): Observable[B] =
    Observable(observer => {
      val composite = CompositeCancelable()

      // TODO - think about resource release
      //      - we should do reference counting here
      //      - because observer.onComplete() is missing

      composite += fn(new Observer[A] {
        def onNext(elem: A) = {
          val s = SingleAssignmentCancelable()
          composite += s

          s() = f(elem).fn(new Observer[B] {
            def onNext(elem: B): Unit =
              observer.onNext(elem)

            def onCompleted(): Unit = {
              composite -= s
              s.cancel()
            }

            def onError(ex: Throwable): Unit =
              try {
                composite -= s
                s.cancel()
              } finally {
                observer.onError(ex)
              }
          })
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onCompleted() = ()
      })

      composite
    })

  final def filter(p: A => Boolean): Observable[A] =
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A) = if (p(elem)) observer.onNext(elem)
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))

  final def subscribeOn(s: Scheduler): Observable[A] =
    Observable(o => s.schedule(_ => fn(o)))

  final def observeOn(s: Scheduler): Observable[A] =
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A) = s.scheduleOnce(observer.onNext(elem))
      def onError(ex: Throwable) = s.scheduleOnce(observer.onError(ex))
      def onCompleted() = s.scheduleOnce(observer.onCompleted())
    }))

  final def take(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to take should be strictly positive")

    Observable(observer => fn(new Observer[A] {
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

  final def takeWhile(p: A => Boolean): Observable[A] =
    Observable(observer => fn(new Observer[A] {
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

  final def foldState[R](initialState: R)(f: (R, A) => FoldState[R]): Observable[R] = {
    @tailrec def loop(state: Atomic[R], next: A): FoldState[R] = {
      val current = state.get
      val result = f(current, next)
      if (state.compareAndSet(expect=current, update=result.value))
        result
      else
        loop(state, next)
    }

    Observable { observer =>
      val state = Atomic(initialState)

      fn(new Observer[A] {
        def onError(ex: Throwable) =
          observer.onError(ex)
        def onCompleted() =
          observer.onCompleted()
        def onNext(nextElem: A) = loop(state, nextElem) match {
          case Cont(_) => // do nothing
          case Emit(result) =>
            observer.onNext(result)
        }
      })
    }
  }

  final def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
    Observable { observer =>
      val state = Atomic(initial)

      fn(new Observer[A] {
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

  final def buffer(count: Int, skip: Int = 0): Observable[Queue[A]] = {
    require(count > 0, "count should be strictly positive")
    require(skip >= 0, "skip should be positive")

    @tailrec def takeRight(q: Queue[A], nr: Int = 0): Queue[A] =
      if (nr > 0) takeRight(q.tail, nr - 1) else q

    val folded = foldState[(Queue[A], Int)]((Queue.empty, count)) { (acc, nextElem) =>
      val (q, steps) = acc
      val newQ = q.enqueue(nextElem)
      val limitedQ = takeRight(newQ, newQ.length - count)

      if (steps > 0)
        Cont((limitedQ, steps - 1))
      else
        Emit((limitedQ, skip))
    }

    folded.map(_._1)
  }

  final def asFuture(implicit ec: ExecutionContext): Future[Option[A]] = {
    val promise = Promise[Option[A]]()
    val sub = subscribe(new Observer[A] {
      @volatile var lastValue = Option.empty[A]

      def onNext(elem: A): Unit = {
        lastValue = Some(elem)
      }

      def onCompleted(): Unit = {
        promise.trySuccess(lastValue)
      }

      def onError(ex: Throwable): Unit = {
        promise.tryFailure(ex)
      }
    })

    val future = promise.future
    future.onComplete { case _ => sub.cancel() }
    future
  }

  final def synchronized: Observable[A] =
    Observable(observer => fn(SynchronizedObserver(observer)))
}

object Observable extends ObservableUtils {
  def apply[A](f: Observer[A] => Cancelable): Observable[A] =
    new Observable[A] {
      def fn(observer: Observer[A]) =
        f(observer)
    }
}
