package monifu.reactive.subjects

import scala.concurrent.{Promise, Future}
import monifu.reactive.api.{SafeObserver, Ack}
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.Observer
import scala.collection.mutable
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.extensions._


final class PublishSubject[T] private (s: Scheduler) extends Subject[T] { self =>
  implicit val scheduler = s
  private[this] val subscribers = mutable.Map.empty[Observer[T], Future[Ack]]
  private[this] var isDone = false

  def subscribeFn(observer: Observer[T]): Unit =
    self.synchronized {
      val safe = SafeObserver(observer)
      if (!isDone)
        subscribers.update(safe, Continue)
      else
        safe.onComplete()
    }

  def onNext(elem: T): Future[Ack] =
    self.synchronized {
      if (!isDone)
        if (subscribers.nonEmpty) {
          val counter = Atomic(subscribers.size)
          val p = Promise[Continue]()

          def completeCountdown(): Unit =
            if (counter.decrementAndGet() == 0) p.success(Continue)

          for ((observer, ack) <- subscribers) {
            val f = ack.unsafeFlatMap {
              case Continue => observer.onNext(elem)
              case Done => Done
            }

            subscribers(observer) = f

            f.unsafeOnSuccess {
              case Done =>
                self.synchronized(subscribers.remove(observer))
                completeCountdown()
              case Continue =>
                completeCountdown()
            }
          }

          p.future
        }
        else
          Continue
      else
        Done
    }

  def onError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      isDone = true

      if (subscribers.nonEmpty) {
        for ((observer, ack) <- subscribers)
          ack.unsafeOnSuccess {
            case Continue => observer.onError(ex)
          }

        subscribers.clear()
      }
    }
  }

  def onComplete() = self.synchronized {
    if (!isDone) {
      isDone = true

      if (subscribers.nonEmpty) {
        for ((observer, ack) <- subscribers)
          ack.unsafeOnSuccess {
            case Continue => observer.onComplete()
          }

        subscribers.clear()
      }
    }
  }
}

object PublishSubject {
  def apply[T]()(implicit scheduler: Scheduler): PublishSubject[T] =
    new PublishSubject[T](scheduler)
}