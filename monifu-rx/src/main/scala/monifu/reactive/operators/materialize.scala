package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Notification.{OnComplete, OnError, OnNext}
import monifu.reactive.{Notification, Observable, Ack, Observer}
import monifu.reactive.internals._
import scala.concurrent.Future


object materialize {
  /**
   * Implementation for [[Observable.materialize]].
   */
  def apply[T](implicit s: Scheduler) = (source: Observable[T]) =>
    Observable.create[Notification[T]] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var ack = Continue : Future[Ack]

        def onNext(elem: T): Future[Ack] = {
          ack = observer.onNext(OnNext(elem))
          ack
        }

        def onError(ex: Throwable): Unit =
          ack.onContinue {
            observer.onNext(OnError(ex))
            observer.onComplete()
          }

        def onComplete(): Unit =
          ack.onContinue {
            observer.onNext(OnComplete)
            observer.onComplete()
          }
      })
    }
}
