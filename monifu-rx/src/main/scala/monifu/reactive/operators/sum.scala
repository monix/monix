package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object sum {
  /**
   * Implementation for [[Observable.sum]].
   */
  def apply[T](implicit ev: Numeric[T]) = (source: Observable[T]) =>
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var result = ev.zero

        def onNext(elem: T): Future[Ack] = {
          result = ev.plus(result, elem)
          Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete(): Unit = {
          observer.onNext(result)
          observer.onComplete()
        }
      })
    }
}
