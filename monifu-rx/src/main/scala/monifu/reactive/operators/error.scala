package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observable, Observer}

object error {
  /**
   * Implements [[Observable.error]].
   */
  def apply[T]() = (source: Observable[T]) =>
    Observable.create[Throwable] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) =
          Continue

        def onComplete(): Unit =
          observer.onComplete()

        def onError(ex: Throwable): Unit = {
          observer.onNext(ex)
          observer.onComplete()
        }
      })
    }
}
