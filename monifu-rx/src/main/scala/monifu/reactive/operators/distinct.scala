package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}
import scala.collection.mutable


object distinct {
  /**
   * Implementation for [[Observable.distinct]].
   */
  def distinct[T](source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[T]

        def onNext(elem: T) = {
          if (set(elem)) Continue
          else {
            set += elem
            observer.onNext(elem)
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  def distinctBy[T, U](source: Observable[T])(fn: T => U) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[U]

        def onNext(elem: T) = {
          val key = fn(elem)
          if (set(key)) Continue
          else {
            set += key
            observer.onNext(elem)
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Implementation for `Observable.distinctUntilChanged`.
   */
  def untilChanged[T](source: Observable[T]) =
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
  def untilChangedBy[T, U](source: Observable[T])(fn: T => U) =
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
