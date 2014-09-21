package monifu.reactive.operators

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observer, Observable}

import scala.concurrent.Future
import scala.util.control.NonFatal

object dropWhileWithIndex {
  /**
   * Implementation for [[Observable.dropWhileWithIndex]].
   */
  def apply[T](p: (T, Int) => Boolean)(source: Observable[T]): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        var continueDropping = true
        var index = 0

        def onNext(elem: T) = {
          if (continueDropping) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isStillInvalid = p(elem, index)
              streamError = false

              if (isStillInvalid) {
                index += 1
                Continue
              }
              else {
                continueDropping = false
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
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
