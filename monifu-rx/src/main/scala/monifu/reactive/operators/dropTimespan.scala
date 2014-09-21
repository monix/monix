package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object dropTimespan {
  /**
   * Implementation for `Observable.drop(timespan)`.
   */
  def apply[T](timespan: FiniteDuration)(implicit s: Scheduler) =
    (source: Observable[T]) =>
      Observable.create[T] { observer =>
        source.unsafeSubscribe(new Observer[T] {
          @volatile private[this] var shouldDrop = true

          private[this] val task =
            s.scheduleOnce(timespan, {
              shouldDrop = false
            })

          def onNext(elem: T): Future[Ack] = {
            if (shouldDrop)
              Continue
            else
              observer.onNext(elem)
          }

          def onError(ex: Throwable): Unit = {
            task.cancel()
            observer.onError(ex)
          }

          def onComplete(): Unit = {
            task.cancel()
            observer.onComplete()
          }
        })
      }
}
