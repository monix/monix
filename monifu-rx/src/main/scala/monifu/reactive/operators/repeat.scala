package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.subjects.ReplaySubject
import monifu.reactive.{Ack, Observer, Subject, Observable}
import monifu.reactive.internals._
import scala.concurrent.Future

object repeat {
  /**
   * Implementation for [[Observable.repeat]].
   */
  def apply[T](implicit s: Scheduler) = (source: Observable[T]) => {
    // recursive function - subscribes the observer again when
    // onComplete happens
    def loop(subject: Subject[T, T], observer: Observer[T]): Unit =
      subject.unsafeSubscribe(new Observer[T] {
        private[this] var lastResponse = Continue : Future[Ack]

        def onNext(elem: T) = {
          lastResponse = observer.onNext(elem)
          lastResponse
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete(): Unit =
          lastResponse.onContinue(loop(subject, observer))
      })

    Observable.create[T] { observer =>
      val subject = ReplaySubject[T]()
      loop(subject, observer)

      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subject.onNext(elem)
        }
        def onError(ex: Throwable): Unit = {
          subject.onError(ex)
        }
        def onComplete(): Unit = {
          subject.onComplete()
        }
      })
    }
  }

}
