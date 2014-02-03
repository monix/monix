package monifu.rx

import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.Atomic

trait Observable[+A] {
  def subscribe(observer: Observer[A]): Subscription

  def subscribe(f: A => Unit): Subscription = 
    subscribe(
      onNext = (elem: A) => f(elem),
      onError = (ex: Throwable) => throw ex,
      onCompleted = () => {}
    )

  def subscribe(onNext: A => Unit, onError: Throwable => Unit, onCompleted: () => Unit): Subscription =
    subscribe(Observer(onNext, onError, onCompleted))

  def map[B](f: A => B): Observable[B] = 
    Observable[B](observer => subscribe(
      (elem: A) => observer.onNext(f(elem)),
      (ex: Throwable) => observer.onError(ex),
      () => observer.onCompleted()
    ))

  def flatMap[B](f: A => Observable[B]): Observable[B] = 
    Observable[B] { observer => 
      val composite = CompositeSubscription()

      composite += subscribe(
        onError = observer.onError,
        onCompleted = observer.onCompleted,
        onNext = (elem: A) => 
          composite += f(elem).subscribe(observer)
      )

      composite
    }

  def filter(p: A => Boolean): Observable[A] =
    Observable(observer => subscribe(
      onError = observer.onError,
      onCompleted = observer.onCompleted,
      onNext = (elem: A) => 
        if (p(elem)) observer.onNext(elem)
    ))

  def subscribeOn(s: Scheduler): Observable[A] =
    Observable(observer =>
      s.scheduleR(_ => subscribe(observer))
    )
  
  def observeOn(s: Scheduler): Observable[A] =
    Observable(observer => subscribe(
      onNext = elem => s.schedule(observer.onNext(elem)),
      onError = ex => s.schedule(observer.onError(ex)),
      onCompleted = () => s.schedule(observer.onCompleted())
    ))
}

object Observable {
  def apply[A](f: Observer[A] => Subscription): Observable[A] = 
    new Observable[A] {
      def subscribe(observer: Observer[A]) = f(observer)
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
      Subscription {}
    }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { observer =>
      val counter = Atomic(0L)

      s.schedule(period, period) {
        val nr = counter.getAndIncrement()
        observer.onNext(nr)
      }
    }
}


