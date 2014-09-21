package monifu.reactive.operators

import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future

object defaultIfEmpty {
  /**
   * Implementation for [[monifu.reactive.Observable.defaultIfEmpty]].
   */
  def apply[T](default: T)(source: Observable[T]): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var isEmpty = true

        def onNext(elem: T): Future[Ack] = {
          if (isEmpty) isEmpty = false
          observer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (isEmpty) observer.onNext(default)
          observer.onComplete()
        }
      })
    }
}
