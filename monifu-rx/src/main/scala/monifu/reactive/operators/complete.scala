package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}

object complete {
  /**
   * Implements [[Observable.complete]].
   */
  def apply[T]() = (source: Observable[T]) =>
    Observable.create[Nothing] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onError(ex: Throwable): Unit =
          observer.onError(ex)
        def onComplete(): Unit =
          observer.onComplete()
      })
    }
}
