package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object count {
  /**
   * Implementation for [[Observable.count]].
   */
  def apply[T]() = (source: Observable[T]) =>
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
}
