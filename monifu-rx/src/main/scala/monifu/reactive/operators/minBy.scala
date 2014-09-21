package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object minBy {
  /**
   * Implementation for [[Observable.minBy]].
   */
  def apply[T,U](f: T => U)(implicit ev: Ordering[U]) =
    (source: Observable[T]) =>
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
}
