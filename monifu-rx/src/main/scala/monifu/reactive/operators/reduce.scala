package monifu.reactive.operators

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future
import scala.util.control.NonFatal

object reduce {
  /**
   * Implementation for [[monifu.reactive.Observable.reduce]].
   */
  def apply[T](op: (T, T) => T)(source: Observable[T]) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var state: T = _
        private[this] var isFirst = true
        private[this] var wasApplied = false

        def onNext(elem: T): Future[Ack] = {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            if (isFirst) {
              isFirst = false
              state = elem
            }
            else {
              state = op(state, elem)
              if (!wasApplied) wasApplied = true
            }

            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onComplete() = {
          if (wasApplied) {
            observer.onNext(state)
            observer.onComplete()
          }
          else
            observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }
}
