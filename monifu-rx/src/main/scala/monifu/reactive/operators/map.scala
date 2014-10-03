package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}
import scala.concurrent.Future
import scala.util.control.NonFatal

object map {
  /**
   * Implementation for [[Observable.map]].
   */
  def apply[T,U](source: Observable[T])(f: T => U): Observable[U] = {
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            val next = f(elem)
            streamError = false
            observer.onNext(next)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete() =
          observer.onComplete()
      })
    }
  }
}
