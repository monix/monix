package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._
import scala.concurrent.Future


object debug {
  /**
   * Implementation for [[Observable.dump]].
   */
  def dump[T](source: Observable[T], prefix: String)(implicit s: Scheduler) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var pos = 0

        def onNext(elem: T): Future[Ack] = {
          System.out.println(s"$pos: $prefix-->$elem")
          pos += 1
          val f = observer.onNext(elem)
          f.onCancel { pos += 1; System.out.println(s"$pos: $prefix canceled") }
          f
        }

        def onError(ex: Throwable) = {
          System.out.println(s"$pos: $prefix-->$ex")
          pos += 1
          observer.onError(ex)
        }

        def onComplete() = {
          System.out.println(s"$pos: $prefix completed")
          pos += 1
          observer.onComplete()
        }
      })
    }
}
