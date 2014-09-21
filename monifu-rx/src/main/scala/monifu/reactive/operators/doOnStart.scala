package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}

import scala.util.control.NonFatal

object doOnStart {
  /**
   * Implementation for [[Observable.doOnStart]].
   */
  def apply[T](cb: T => Unit)(source: Observable[T]): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var isStarted = false

        def onNext(elem: T) = {
          if (!isStarted) {
            isStarted = true
            var streamError = true
            try {
              cb(elem)
              streamError = false
              observer.onNext(elem)
            }
            catch {
              case NonFatal(ex) =>
                observer.onError(ex)
                Cancel
            }
          }
          else
            observer.onNext(elem)
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }
}
