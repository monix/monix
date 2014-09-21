package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Continue, Cancel}
import monifu.reactive.{Ack, Observer, Observable}

import scala.collection.mutable
import scala.concurrent.Future

object takeRight {
  /**
   * Implementation for [[monifu.reactive.Observable.takeRight]].
   */
  def apply[T](n: Int)(implicit s: Scheduler) =
    (source: Observable[T]) => {
      Observable.create[T] { observer =>
        source.unsafeSubscribe(new Observer[T] {
          private[this] val queue = mutable.Queue.empty[T]
          private[this] var queued = 0

          def onNext(elem: T): Future[Ack] = {
            if (n <= 0)
              Cancel
            else if (queued < n) {
              queue.enqueue(elem)
              queued += 1
            }
            else {
              queue.enqueue(elem)
              queue.dequeue()
            }
            Continue
          }

          def onError(ex: Throwable): Unit = {
            queue.clear()
            observer.onError(ex)
          }

          def onComplete(): Unit = {
            Observable.from(queue).unsafeSubscribe(observer)
          }
        })
      }
    }

}
