package monifu.reactive.operators

import monifu.reactive.{Observer, Observable}

object endWithError {
  /**
   * Implements [[Observable.endWithError]].
   */
  def apply[T](error: Throwable)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = observer.onNext(elem)
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onError(error)
      })
    }
}
