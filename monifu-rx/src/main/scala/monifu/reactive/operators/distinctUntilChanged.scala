package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observable, Observer}

object distinctUntilChanged {
  /**
   * Implementation for `Observable.distinctUntilChanged`.
   */
  def apply[T]()(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastElem: T = _

        def onNext(elem: T) = {
          if (isFirst) {
            lastElem = elem
            isFirst = false
            observer.onNext(elem)
          }
          else if (lastElem != elem) {
            lastElem = elem
            observer.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Implementation for `Observable.distinctUntilChanged(fn)`.
   */
  def apply[T, U](fn: T => U)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastKey: U = _

        def onNext(elem: T) = {
          val key = fn(elem)
          if (isFirst) {
            lastKey = fn(elem)
            isFirst = false
            observer.onNext(elem)
          }
          else if (lastKey != key) {
            lastKey = key
            observer.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }
}
