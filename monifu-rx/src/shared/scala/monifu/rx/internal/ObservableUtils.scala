package monifu.rx.internal

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.rx.Observable
import concurrent.duration._

trait ObservableUtils extends Any { this: Observable.type =>
  def empty[A]: Observable[A] =
    Observable { subscriber =>
      if (!subscriber.isCanceled) subscriber.onCompleted()
      subscriber
    }

  def unit[A](elem: A): Observable[A] =
    Observable { subscriber =>
      if (!subscriber.isCanceled) {
        subscriber.onNext(elem)
        subscriber.onCompleted()
      }

      subscriber
    }

  def error(ex: Throwable): Observable[Nothing] =
    Observable { subscriber =>
      if (!subscriber.isCanceled) subscriber.onError(ex)
      subscriber
    }

  def never: Observable[Nothing] =
    Observable { subscriber => subscriber }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { subscriber =>
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
    Observable[T] { subscriber =>
      if (!subscriber.isCanceled) {
        for (i <- sequence)
          if (!subscriber.isCanceled)
            subscriber.onNext(i)

        if (!subscriber.isCanceled)
          subscriber.onCompleted()
      }

      subscriber
    }
}
