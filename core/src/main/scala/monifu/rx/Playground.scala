package monifu.rx

import scala.language.higherKinds
import scala.concurrent.Future
import monifu.concurrent.atomic.Atomic
import scala.collection.SortedSet
import scala.concurrent.duration._


object Playground {

  def pushEvents[T](duration: FiniteDuration, events: Seq[T]): Observable[T] = {
    def createThread(observer: Observer[T], isCanceled: Atomic[Boolean]) = 
      new Thread(new Runnable() {
        override def run(): Unit = {
          try {
            for (e <- events; if !isCanceled.get) {
              observer.onNext(e)
              Thread.sleep(duration.toMillis)
            }

            if (!isCanceled.get)
              observer.onComplete()
          }
          catch {
            case _: InterruptedException =>
              // do nothing
            case ex: Exception =>
              observer.onError(ex)
          }
        }
      })

    Observable({ observer: Observer[T] =>
      val isCanceled = Atomic(false)
      val th = createThread(observer, isCanceled)
      th.start()

      new Subscription {
        def unsubscribe(): Unit = {
          th.interrupt
          th.join
        }
      }
    })
  }

}