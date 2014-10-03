package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}
import monifu.concurrent.extensions._
import monifu.reactive.internals._
import scala.concurrent.Future
import scala.util.control.NonFatal

object doWork {
  /**
   * Implementation for [[Observable.doWork]].
   */
  def onNext[T](cb: T => Unit)(source: Observable[T]) =
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

  /**
   * Implementation for [[Observable.doOnComplete]].
   */
  def onComplete[T](cb: => Unit)(implicit s: Scheduler) = (source: Observable[T]) =>
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] val wasExecuted = Atomic(false)

        private[this] def execute() = {
          if (wasExecuted.compareAndSet(expect=false, update=true))
            try cb catch {
              case NonFatal(ex) =>
                s.reportFailure(ex)
            }
        }

        def onNext(elem: T) = {
          val f = observer.onNext(elem)
          f.onCancel(execute())
          f
        }

        def onError(ex: Throwable): Unit = {
          try observer.onError(ex) finally
            s.executeNow {
              execute()
            }
        }

        def onComplete(): Unit = {
          try observer.onComplete() finally
            s.executeNow {
              execute()
            }
        }
      })
    }

  /**
   * Implementation for [[Observable.doOnStart]].
   */
  def onStart[T](cb: T => Unit)(source: Observable[T]): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var isStarted = false

        def onNext(elem: T) = {
          if (!isStarted) {
            isStarted = true
            var streamError = true
            try {
              cb(elem)
              streamError = false
              observer.onNext(elem)
            }
            catch {
              case NonFatal(ex) =>
                observer.onError(ex)
                Cancel
            }
          }
          else
            observer.onNext(elem)
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }
}
