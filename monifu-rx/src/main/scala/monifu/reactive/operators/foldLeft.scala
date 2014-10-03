package monifu.reactive.operators

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observable, Ack, Observer}

import scala.concurrent.Future
import scala.util.control.NonFatal

object foldLeft {
  /**
   * Implementation for [[Observable.foldLeft]].
   */
  def apply[T, R](source: Observable[T], initial: R)(op: (R, T) => R) =
    Observable.create[R] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var state = initial

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            state = op(state, elem)
            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onComplete() = {
          observer.onNext(state)
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }
}
