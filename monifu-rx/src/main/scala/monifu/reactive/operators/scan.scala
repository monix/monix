package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observable, Ack, Observer}

import scala.concurrent.Future
import scala.util.control.NonFatal

object scan {
  /**
   * Implementation for [[Observable.scan]].
   */
  def apply[T, R](source: Observable[T], initial: R)(op: (R, T) => R) =
    Observable.create[R] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            state = op(state, elem)
            streamError = false
            observer.onNext(state)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
          }
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }
}
