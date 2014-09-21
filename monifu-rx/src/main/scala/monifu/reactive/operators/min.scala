package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object min {
  /**
   * Implementation for [[Observable.min]].
   */
  def apply[T, U >: T](implicit ev: Ordering[U]) = (source: Observable[T]) =>
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
}
