package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


object takeTimespan {
  def apply[T](timespan: FiniteDuration)(implicit s: Scheduler) =
    (source: Observable[T]) =>
      Observable.create[T] { observer =>
        source.unsafeSubscribe(new Observer[T] {
          private[this] val lock = SpinLock()
          private[this] var isDone = false

          private[this] val task =
            s.scheduleOnce(timespan, onComplete())

          def onNext(elem: T): Future[Ack] =
            lock.enter {
              if (!isDone)
                observer.onNext(elem).onCancel {
                  lock.enter {
                    task.cancel()
                    isDone = true
                  }
                }
              else
                Cancel
            }

          def onError(ex: Throwable): Unit =
            lock.enter {
              if (!isDone) {
                isDone = true
                observer.onError(ex)
              }
            }

          def onComplete(): Unit =
            lock.enter {
              if (!isDone) {
                isDone = true
                observer.onComplete()
              }
            }
        })
      }
}
