package monifu.rx.internal

import monifu.concurrent.{Scheduler, Cancelable}
import monifu.concurrent.atomic.Atomic
import monifu.rx.Observable
import concurrent.duration._

trait ObservableUtils extends Any { this: Observable.type =>
  def unit[A](elem: A): Observable[A] =
    Observable { subscriber =>
      if (!subscriber.isCanceled) {
        subscriber.onNext(elem)
        subscriber.onCompleted()
      }      
      subscriber.add(Cancelable.alreadyCanceled)
    }

  def error(ex: Throwable): Observable[Nothing] =
    Observable { subscriber =>
      if (!subscriber.isCanceled) subscriber.onError(ex)
      subscriber.add(Cancelable.alreadyCanceled)
    }

  def never: Observable[Nothing] =
    Observable { subscriber => subscriber }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { subscriber =>
      val counter = Atomic(0L)
      val task = s.scheduleRepeated(period, period, {
        val nr = counter.getAndIncrement()
        if (!subscriber.isCanceled) subscriber.onNext(nr)
      })

      subscriber.add(task)
    }

  def fromIterable[T](iterable: Iterable[T])(implicit s: Scheduler): Observable[T] =
    fromSequence(iterable)

  def fromSequence[T](sequence: TraversableOnce[T])(implicit s: Scheduler): Observable[T] = {
    val obs = Observable[T] { subscriber =>
      if (!subscriber.isCanceled) {
        for (i <- sequence)
          if (!subscriber.isCanceled)
            subscriber.onNext(i)

        if (!subscriber.isCanceled)
          subscriber.onCompleted()
      }

      subscriber
    }

    obs.subscribeOn(s)
  }

}
