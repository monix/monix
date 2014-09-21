package monifu.reactive.operators

import monifu.concurrent.atomic.AtomicBoolean
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}

object takeWhileRefIsTrue {
  /**
   * Implementation for [[monifu.reactive.Observable.takeWhileRefIsTrue]].
   */
  def apply[T](ref: AtomicBoolean)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            if (ref.get)
              observer.onNext(elem)
            else {
              shouldContinue = false
              observer.onComplete()
              Cancel
            }
          }
          else
            Cancel
        }

        def onComplete() = {
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }
}
