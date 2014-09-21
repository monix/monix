package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object maxBy {

  def apply[T,U](f: T => U)(implicit ev: Ordering[U]) =
    (source: Observable[T]) => Observable.create[T] { observer =>
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
}
