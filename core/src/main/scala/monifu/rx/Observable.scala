package monifu.rx

import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Scheduler
import scala.concurrent.ExecutionContext

trait Observer[-T] {
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onComplete(): Unit  
}

trait Observable[+A] {
  def subscribe(observer: Observer[A]): Subscription

  def subscribe(f: A => Unit): Subscription = 
    subscribe(new Observer[A] {
      def onNext(elem: A) = f(elem)
      def onError(ex: Throwable) = throw ex
      def onComplete(): Unit = {}
    })

  def subscribe(next: A => Unit, error: Throwable => Unit, complete: () => Unit): Subscription = 
    subscribe(new Observer[A] {
      def onNext(elem: A) = next(elem)
      def onError(ex: Throwable) = error(ex)
      def onComplete() = complete()
    })

  def map[B](f: A => B): Observable[B] = 
    Observable[B](observer => subscribe(
      (elem: A) => observer.onNext(f(elem)),
      (ex: Throwable) => observer.onError(ex),
      () => observer.onComplete()
    ))

  def flatMap[B](f: A => Observable[B]): Observable[B] = 
    Observable[B] { observer => 
      val composite = CompositeSubscription()

      composite += subscribe(new Observer[A] {
        def onNext(elem: A) = 
          composite += f(elem).subscribe(observer)
        def onError(ex: Throwable) = 
          observer.onError(ex)
        def onComplete() = 
          observer.onComplete()
      })

      composite
    }

  def filter(p: A => Boolean): Observable[A] =
    Observable(observer => subscribe(
      (elem: A) => if (p(elem)) observer.onNext(elem),
      (ex: Throwable) => observer.onError(ex),
      () => observer.onComplete()
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
      val task = s.schedule(period, period) {
        val nr = counter.incrementAndGet()
        observer.onNext(nr)
      }

      Subscription {
        task.cancel()
      }
    }
}


