package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}

object drop {
  /**
   * Implementation for [[Observable.drop]].
   */
  def apply[T](n: Int)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var count = 0L

        def onNext(elem: T) = {
          if (count < n) {
            count += 1
            Continue
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }
}
