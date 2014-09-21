package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}

import scala.concurrent.Future
import scala.util.control.NonFatal

object doWork {
  /**
   * Implementation for [[Observable.doWork]].
   */
  def apply[T](cb: T => Unit)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()

        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            cb(elem)
            streamError = false
            observer.onNext(elem)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }
      })
    }
}
