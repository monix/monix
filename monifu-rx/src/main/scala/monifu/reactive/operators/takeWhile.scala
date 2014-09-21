package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}

import scala.concurrent.Future
import scala.util.control.NonFatal

object takeWhile {
  /**
   * Implementation for [[monifu.reactive.Observable.takeWhileRefIsTrue]].
   */
  def apply[T](p: T => Boolean)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isValid = p(elem)
              streamError = false
              if (isValid) {
                observer.onNext(elem)
              }
              else {
                shouldContinue = false
                observer.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) {
                  observer.onError(ex)
                  Cancel
                }
                else
                  Future.failed(ex)
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
