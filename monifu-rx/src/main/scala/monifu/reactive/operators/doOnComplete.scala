package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observable, Observer}
import monifu.concurrent.extensions._
import scala.util.control.NonFatal


object doOnComplete {
  /**
   * Implementation for [[Observable.doOnComplete]].
   */
  def apply[T](cb: => Unit)(implicit s: Scheduler) = (source: Observable[T]) =>
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
          f.onSuccess { case Cancel => execute() }
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
}
