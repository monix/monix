package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}
import scala.concurrent.Future

object math {
  /**
   * Implementation for [[Observable.count]].
   */
  def count[T](source: Observable[T]) =
    Observable.create[Long] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var count = 0l

        def onNext(elem: T): Future[Ack] = {
          count += 1
          Continue
        }

        def onComplete() = {
          observer.onNext(count)
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Implementation for [[Observable.sum]].
   */
  def sum[T](source: Observable[T])(implicit ev: Numeric[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var result = ev.zero

        def onNext(elem: T): Future[Ack] = {
          result = ev.plus(result, elem)
          Continue
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete(): Unit = {
          observer.onNext(result)
          observer.onComplete()
        }
      })
    }

  /**
   * Implementation for [[Observable.minBy]].
   */
  def minBy[T,U](source: Observable[T])(f: T => U)(implicit ev: Ordering[U]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
          else {
            observer.onNext(minValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Implementation for [[Observable.min]].
   */
  def min[T](source: Observable[T])(implicit ev: Ordering[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
          else {
            observer.onNext(minValue)
            observer.onComplete()
          }
        }
      })
    }

  def maxBy[T,U](source: Observable[T])(f: T => U)(implicit ev: Ordering[U]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
          else {
            observer.onNext(maxValue)
            observer.onComplete()
          }
        }
      })
    }

  /**
   * Implementation for [[Observable.max]].
   */
  def max[T](source: Observable[T])(implicit ev: Ordering[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
          else {
            observer.onNext(maxValue)
            observer.onComplete()
          }
        }
      })
    }
}
