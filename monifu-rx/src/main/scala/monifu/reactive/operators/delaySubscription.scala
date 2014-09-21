package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Observable

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

object delaySubscription {
  /**
   * Implementation for [[Observable.delaySubscription]].
   */
  def apply[T](future: Future[_])(implicit s: Scheduler) =
    (source: Observable[T]) =>
      Observable.create[T] { observer =>
        future.onComplete {
          case Success(_) =>
            source.unsafeSubscribe(observer)
          case Failure(ex) =>
            observer.onError(ex)
        }
      }

  /**
   * Implementation for [[Observable.delaySubscription]].
   */
  def apply[T](timespan: FiniteDuration)(implicit s: Scheduler) =
    (source: Observable[T]) =>
      source.delaySubscription {
        val p = Promise[Unit]()
        s.scheduleOnce(timespan, p.success(()))
        p.future
      }
}
