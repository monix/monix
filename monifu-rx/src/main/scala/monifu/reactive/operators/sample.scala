package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.Observable
import monifu.reactive.observers.SynchronousObserver

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object sample {
  /**
   * Implementation for `Observable.sample(initialDelay, delay)`.
   */
  def apply[T](initialDelay: FiniteDuration, delay: FiniteDuration)(implicit s: Scheduler) =
    (source: Observable[T]) => Observable.create[T] { observer =>
      source.unsafeSubscribe(new SynchronousObserver[T] {
        private[this] val lock = SpinLock()
        // must be synchronized by lock
        private[this] var isDone = false
        // must be synchronized by lock
        private[this] var lastEvent: T = _
        // must be synchronized by lock
        private[this] var valueHappened = false

        private[this] val task =
          s.scheduleRecursive(initialDelay, delay, { reschedule =>
            lock.enter {
              if (!isDone) {
                if (valueHappened) {
                  valueHappened = false
                  val result =
                    try observer.onNext(lastEvent) catch {
                      case NonFatal(ex) =>
                        Future.failed(ex)
                    }

                  result match {
                    case sync if sync.isCompleted =>
                      sync.value.get match {
                        case Success(Continue) =>
                          reschedule()
                        case Success(Cancel) =>
                          isDone = true
                        case Failure(ex) =>
                          onError(ex)
                      }
                    case async =>
                      async.onComplete {
                        case Success(Continue) =>
                          reschedule()
                        case Success(Cancel) =>
                          lock.enter { isDone = true }
                        case Failure(ex) =>
                          onError(ex)
                      }
                  }
                }
                else {
                  reschedule()
                }
              }
            }
          })

        def onNext(elem: T) =
          lock.enter {
            if (isDone) Cancel
            else {
              valueHappened = true
              lastEvent = elem
              Continue
            }
          }

        def onError(ex: Throwable): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              task.cancel()
              observer.onError(ex)
            }
          }

        def onComplete(): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              task.cancel()
              observer.onComplete()
            }
          }
      })
    }
}
