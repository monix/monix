package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object max {
  /**
   * Implementation for [[Observable.max]].
   */
  def apply[T](implicit ev: Ordering[T]) = (source: Observable[T]) =>
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
