package monifu.rx

import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import monifu.rx.FoldState.{Cont, Emit}
import scala.collection.immutable.Queue
import monifu.rx.subscriptions._
import scala.concurrent.{Promise, Future}


trait Observable[+A]  {
  protected def fn(observer: Observer[A]): Subscription

  def subscribe(observer: Observer[A]): Subscription = {
    val sub = SingleAssignmentSubscription()
    sub() = fn(SafeObserver(observer, sub))
    sub
  }

  def subscribe(f: A => Unit): Subscription =
    subscribe(new Observer[A] {
      def onNext(elem: A): Unit = f(elem)
      def onError(ex: Throwable): Unit = throw ex
      def onCompleted(): Unit = ()
    })
  
  def subscribe(next: A => Unit, error: Throwable => Unit, completed: () => Unit): Subscription =
    subscribe(new Observer[A] {
      def onNext(elem: A): Unit = next(elem)
      def onCompleted(): Unit = completed()
      def onError(ex: Throwable): Unit = error(ex)
    })

  def map[B](f: A => B): Observable[B] =
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A): Unit = observer.onNext(f(elem))
      def onCompleted(): Unit = observer.onCompleted()
      def onError(ex: Throwable): Unit = observer.onError(ex)
    }))

  def flatMap[B](f: A => Observable[B]): Observable[B] = 
    Observable(observer => {
      val composite = CompositeSubscription()

      composite += fn(new Observer[A] {
        def onNext(elem: A) = {
          val s = SingleAssignmentSubscription()
          composite += s

          s() = f(elem).fn(new Observer[B] {
            def onNext(elem: B): Unit =
              observer.onNext(elem)

            def onCompleted(): Unit = {
              composite -= s
              s.unsubscribe()
            }

            def onError(ex: Throwable): Unit = {
              composite -= s
              s.unsubscribe()
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
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A) = if (p(elem)) observer.onNext(elem)
      def onError(ex: Throwable) = observer.onError(ex)
      def onCompleted() = observer.onCompleted()
    }))

  def subscribeOn(s: Scheduler): Observable[A] =
    Observable(o => s.scheduleR(_ => fn(o)))
  
  def observeOn(s: Scheduler): Observable[A] =
    Observable(observer => fn(new Observer[A] {
      def onNext(elem: A) = s.schedule(observer.onNext(elem))
      def onError(ex: Throwable) = s.schedule(observer.onError(ex))
      def onCompleted() = s.schedule(observer.onCompleted())
    }))

  def take(nr: Int): Observable[A] = {
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
      })
    )
  }

  def takeWhile(p: A => Boolean): Observable[A] =
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

  def foldLeft[R](initial: R)(f: (R, A) => R): Observable[R] =
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

  def asFuture: Future[Option[A]] = {
    val promise = Promise[Option[A]]()

    subscribe(new Observer[A] {
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

    promise.future
  }
}

object Observable {
  def apply[A](f: Observer[A] => Subscription): Observable[A] =
    new Observable[A] {
      def fn(observer: Observer[A]) =
        f(observer)
    }

  def unit[A](elem: A): Observable[A] =
    Observable[A] { observer => Subscription {
      observer.onNext(elem)
      observer.onCompleted()
    }}

  def never: Observable[Nothing] =
    Observable { observer => Subscription {} }

  def error(ex: Throwable): Observable[Nothing] =
    Observable { observer =>
      observer.onError(ex)
      Subscription.empty
    }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { observer =>
      val counter = Atomic(0L)

      val sub = s.schedule(period, period) {
        val nr = counter.getAndIncrement()
        observer.onNext(nr)
      }

      BooleanSubscription {
        sub.unsubscribe()
      }
    }
}


