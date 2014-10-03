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
  def onFuture[T](source: Observable[T], future: Future[_])(implicit s: Scheduler) =
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
  def onTimespan[T](source: Observable[T], timespan: FiniteDuration)(implicit s: Scheduler) =
    source.delaySubscription {
      val p = Promise[Unit]()
      s.scheduleOnce(timespan, p.success(()))
      p.future
    }
}
