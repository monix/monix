package monifu.rx

import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import monifu.rx.FoldState.{Cont, Emit}
import scala.collection.immutable.Queue
import monifu.concurrent.cancelables._
import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.concurrent.{Scheduler, Cancelable}
import concurrent.duration._
import scala.util.control.NonFatal


trait Observable[+A]  {
  def subscribe(observer: Observer[A]): Cancelable

  def subscribe(f: A => Unit): Cancelable =
    subscribe(new Observer[A] {
      def onNext(elem: A): Unit = f(elem)
      def onError(ex: Throwable): Unit = throw ex
      def onCompleted(): Unit = ()
    })

  def subscribe(next: A => Unit, error: Throwable => Unit, completed: () => Unit): Cancelable =
    subscribe(new Observer[A] {
      def onNext(elem: A): Unit = next(elem)
      def onCompleted(): Unit = completed()
      def onError(ex: Throwable): Unit = error(ex)
    })

  def map[B](f: A => B): Observable[B] =
    Observable(observer => subscribe(new Observer[A] {
      def onNext(elem: A): Unit = observer.onNext(f(elem))
      def onCompleted(): Unit = observer.onCompleted()
      def onError(ex: Throwable): Unit = observer.onError(ex)
    }))

  def flatMap[B](f: A => Observable[B]): Observable[B] =
    Observable(observer => {
      val composite = CompositeCancelable()

      composite += subscribe(new Observer[A] {
        def onNext(elem: A) = {
          val s = SingleAssignmentCancelable()
          composite += s

          s() = f(elem).subscribe(new Observer[B] {
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

  def filter(p: A => Boolean): Observable[A] =
    Observable(observer => subscribe(new Observer[A] {
      def onNext(elem: A) = if (p(elem)) observer.onNext(elem)
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))

  def subscribeOn(s: Scheduler): Observable[A] =
    Observable(o => s.schedule(_ => subscribe(o)))

  def observeOn(s: Scheduler): Observable[A] =
    Observable(observer => subscribe(new Observer[A] {
      def onNext(elem: A) = s.scheduleOnce(observer.onNext(elem))
      def onError(ex: Throwable) = s.scheduleOnce(observer.onError(ex))
      def onCompleted() = s.scheduleOnce(observer.onCompleted())
    }))

  def take(nr: Int): Observable[A] = {
    require(nr > 0, "number of elements to take should be strictly positive")

    Observable(observer => subscribe(new Observer[A] {
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
    })
    )
  }

  def takeWhile(p: A => Boolean): Observable[A] =
    Observable(observer => subscribe(new Observer[A] {
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

  def foldState[R](initialState: R)(f: (R, A) => FoldState[R]): Observable[R] = {
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

      subscribe(new Observer[A] {
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

  def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
    Observable { observer =>
      val state = Atomic(initial)

      subscribe(new Observer[A] {
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

  def buffer(count: Int, skip: Int = 0): Observable[Queue[A]] = {
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

  def asFuture(implicit ec: ExecutionContext): Future[Option[A]] = {
    val promise = Promise[Option[A]]()
    val f = promise.future

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

    f.onComplete { case _ => sub.cancel() }
    f
  }
}

object Observable {
  def apply[A](f: Observer[A] => Cancelable): Observable[A] =
    new Observable[A] {
      def subscribe(observer: Observer[A]) =
        f(observer)
    }

  def unit[A](elem: A): Observable[A] =
    Observable[A] { observer => Cancelable {
      observer.onNext(elem)
      observer.onCompleted()
    }}

  def never: Observable[Nothing] =
    Observable { observer => Cancelable {} }

  def error(ex: Throwable): Observable[Nothing] =
    Observable { observer =>
      observer.onError(ex)
      Cancelable.empty
    }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { observer =>
      val counter = Atomic(0L)

      val sub = s.scheduleRepeated(period, period, {
        val nr = counter.getAndIncrement()
        observer.onNext(nr)
      })

      BooleanCancelable {
        sub.cancel()
      }
    }

  def fromIterable[T](iterable: Iterable[T])(implicit s: Scheduler): Observable[T] =
    fromIterator(iterable.iterator)

  def fromIterator[T](iterator: Iterator[T])(implicit s: Scheduler): Observable[T] =
    Observable { observer =>
      val sub = SingleAssignmentCancelable()

      sub := s.scheduleRecursive(0.seconds, 0.seconds, { reschedule =>
        try {
          if (iterator.hasNext) {
            observer.onNext(iterator.next())
            reschedule()
          }
          else {
            observer.onCompleted()
            sub.cancel()
          }
        }
        catch {
          case NonFatal(ex) =>
            sub.cancel()
            observer.onError(ex)
        }
      })

      sub
    }
}
