package monifu.reactive.operators

import monifu.reactive.{Observable, Ack}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SynchronousObserver

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object whileBusy {
  /**
   * While the destination observer is busy, drop the incoming events.
   *
   * @param cb a callback to be called in case events are dropped
   */
  def drop[T](source: Observable[T])(cb: T => Unit): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new SynchronousObserver[T] {
        private[this] var lastAck = Continue : Future[Ack]
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (!isDone) lastAck match {
            case sync if sync.isCompleted =>
              sync.value.get match {
                case Success(Cancel) =>
                  isDone = true
                  Cancel

                case Failure(ex) =>
                  isDone = true
                  observer.onError(ex)
                  Cancel

                case Success(Continue) =>
                  observer.onNext(elem) match {
                    case Cancel =>
                      isDone = true
                      Cancel
                    case other =>
                      lastAck = other
                      Continue
                  }
              }

            case _ =>
              try {
                cb(elem)
                Continue
              }
              catch {
                case NonFatal(ex) =>
                  observer.onError(ex)
                  Cancel
              }
          }
          else
            Cancel
        }

        def onError(ex: Throwable) =
          if (!isDone) {
            isDone = true
            observer.onError(ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            observer.onComplete()
          }
      })
    }
}
