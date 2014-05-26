package monifu.reactive.subjects

import monifu.concurrent.Scheduler
import scala.collection.mutable
import monifu.reactive.Observer
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.{SafeObserver, Ack}
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.extensions._


final class BehaviorSubject[T] private (initialValue: T, s: Scheduler) extends Subject[T] { self =>
  private[this] var currentValue = initialValue
  private[this] var errorThrown = null : Throwable

  implicit val scheduler = s
  private[this] val subscribers = mutable.Map.empty[Observer[T], Future[Ack]]
  private[this] var isDone = false

  def subscribeFn(observer: Observer[T]): Unit =
    self.synchronized {
      val safe = SafeObserver(observer)
      if (!isDone)
        subscribers.update(safe, safe.onNext(currentValue))
      else if (errorThrown != null)
        safe.onError(errorThrown)
      else
        safe.onComplete()
    }

  def onNext(elem: T): Future[Ack] =
    self.synchronized {
      if (!isDone)
        if (subscribers.nonEmpty) {
          currentValue = elem
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

  def onError(ex: Throwable) = self.synchronized {
    if (!isDone) {
      isDone = true
      errorThrown = ex

      if (subscribers.nonEmpty) {
        for ((observer, ack) <- subscribers)
          ack.unsafeOnSuccess {
            case Continue =>
              observer.onError(ex)
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
            case Continue =>
              observer.onComplete()
          }

        subscribers.clear()
      }
    }
  }
}

object BehaviorSubject {
  def apply[T](initialValue: T)(implicit s: Scheduler): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue, s)
}
