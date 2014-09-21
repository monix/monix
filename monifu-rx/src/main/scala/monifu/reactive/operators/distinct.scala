package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}

import scala.collection.mutable

/**
 * Implementation for [[Observable.distinct]].
 */
object distinct {
  def apply[T]()(source: Observable[T]) =
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

  def apply[T, U](fn: T => U)(source: Observable[T]) =
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
}
