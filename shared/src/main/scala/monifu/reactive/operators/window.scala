package monifu.reactive.operators

import monifu.reactive.internals._
import monifu.reactive.{Observable, Observer}

import scala.concurrent.duration.{Duration, FiniteDuration}

object window {

  private[this] def createBuffer[T](source: Observable[T]) = Observable.create[T] { subscriber =>
    source.unsafeSubscribe(subscriber.observer)(subscriber.scheduler)
  }

  /**
   * Implementation for [[Observable.windowTimed]].
   */
  def timed[T](source: Observable[T], timespan: FiniteDuration): Observable[Observable[T]] = {
    require(timespan >= Duration.Zero, "timespan must be positive")

    Observable.create[Observable[T]] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val timespanMillis = timespan.toMillis
        private[this] var buffer = createBuffer[T](source)
        private[this] var expiresAt = s.currentTimeMillis() + timespanMillis

        def onNext(elem: T) = {
          val rightNow = s.currentTimeMillis()
          buffer = buffer :+ elem

          if (expiresAt <= rightNow) {
            val oldBuffer = buffer.complete
            buffer = createBuffer[T](source)
            expiresAt = rightNow + timespanMillis
            observer.onNext(oldBuffer)
          }
          else
            observer.onNext(buffer)
        }

        def onError(ex: Throwable): Unit = {
          observer.onNext(buffer.endWithError(ex))
            .onContinueSignalError(observer, ex)
        }

        def onComplete(): Unit = {
          observer.onNext(buffer.complete)
            .onContinueSignalComplete(observer)
        }
      })
    }
  }
}