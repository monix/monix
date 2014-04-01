package monifu.rx.internal

import monifu.concurrent.{Scheduler, Cancelable}
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import scala.util.control.NonFatal
import monifu.rx.Observable
import concurrent.duration._


trait ObservableUtils extends Any { this: Observable.type =>
  def unitAsync[A](elem: A)(implicit s: Scheduler): Observable[A] =
    Observable { observer =>
      s.scheduleOnce {
        observer.onNext(elem)
        observer.onCompleted()
      }
    }

  def errorAsync(ex: Throwable)(implicit s: Scheduler): Observable[Nothing] =
    Observable { observer =>
      s.scheduleOnce(observer.onError(ex))
    }

  def never: Observable[Nothing] =
    Observable { observer => Cancelable.empty }

  def interval(period: FiniteDuration)(implicit s: Scheduler): Observable[Long] =
    Observable { observer =>
      val counter = Atomic(0L)

      s.scheduleRepeated(period, period, {
        val nr = counter.getAndIncrement()
        observer.onNext(nr)
      })
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
