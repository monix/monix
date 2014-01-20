package monifu.rx

import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.Atomic
import scala.concurrent.ExecutionContext

trait Observable[+A] {
  def subscribe(observer: Observer[A]): Subscription

  def subscribe(f: A => Unit): Subscription = 
    subscribe(
      onNext = (elem: A) => f(elem),
      onError = (ex: Throwable) => throw ex,
      onComplete = () => {}
    )

  def subscribe(onNext: A => Unit, onError: Throwable => Unit, onComplete: () => Unit): Subscription = 
    subscribe(Observer(onNext, onError, onComplete))

  def map[B](f: A => B): Observable[B] = 
    Observable[B](observer => subscribe(
      (elem: A) => observer.onNext(f(elem)),
      (ex: Throwable) => observer.onError(ex),
      () => observer.onComplete()
    ))

  def flatMap[B](f: A => Observable[B]): Observable[B] = 
    Observable[B] { observer => 
      val composite = CompositeSubscription()

      composite += subscribe(
        onError = observer.onError,
        onComplete = observer.onComplete,
        onNext = (elem: A) => 
          composite += f(elem).subscribe(observer)
      )

      composite
    }

  def filter(p: A => Boolean): Observable[A] =
    Observable(observer => subscribe(
      onError = observer.onError,
      onComplete = observer.onComplete,
      onNext = (elem: A) => 
        if (p(elem)) observer.onNext(elem)
    ))

  def startWith[B >: A](elems: B*): Observable[B] = 
    Observable[B] { observer => 
      for (e <- elems) observer.onNext(e)

      subscribe(
        (elem: B) => observer.onNext(elem),
        (ex: Throwable) => observer.onError(ex),
        () => observer.onComplete()
      )
    }
}

object Observable {
  def apply[A](f: Observer[A] => Subscription): Observable[A] = 
    new Observable[A] {
      def subscribe(observer: Observer[A]) = f(observer)
    }

  def unit[A](elem: A): Observable[A] =
    Observable[A] { observer => Subscription {
      observer.onNext(elem)
      observer.onComplete()
    }}

  def never: Observable[Nothing] =
    Observable { observer => Subscription {} }

  def error(ex: Throwable): Observable[Nothing] =
    Observable { observer => 
      observer.onError(ex)
      Subscription {}
    }

  def interval(period: FiniteDuration)(implicit s: Scheduler, ec: ExecutionContext): Observable[Long] =
    Observable { observer =>
      val counter = Atomic(0L)

      s.schedule(period, period, {
        val nr = counter.incrementAndGet()
        observer.onNext(nr)
      })
    }
}


