package monifu.reactive.operators

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observer, Observable}

import scala.concurrent.Future
import scala.util.control.NonFatal

object filter {
  /**
   * Implementation for [[Observable.filter]].
   */
  def apply[T](source: Observable[T])(p: T => Boolean): Observable[T] = {
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            if (p(elem)) {
              streamError = false
              observer.onNext(elem)
            }
            else
              Continue
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
