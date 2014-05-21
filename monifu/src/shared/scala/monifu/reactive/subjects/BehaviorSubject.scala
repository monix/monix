package monifu.reactive.subjects

import monifu.concurrent.Scheduler
import scala.collection.mutable
import monifu.reactive.Observer
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.atomic.padded.Atomic
import scala.util.{Success, Failure}
import monifu.concurrent.extensions._
import scala.util.control.NonFatal


final class BehaviorSubject[T] private (initialValue: T, s: Scheduler) extends Subject[T] { self =>
  private[this] var currentValue = initialValue
  private[this] var errorThrown = null : Throwable

  implicit val scheduler = s
  private[this] val subscribers = mutable.Map.empty[Observer[T], Future[Ack]]
  private[this] var isDone = false

  def subscribe(observer: Observer[T]): Unit =
    self.synchronized {
      if (!isDone) {
        subscribers.update(observer, observer.onNext(currentValue))
      }
      else if (errorThrown != null) {
        observer.onError(errorThrown)
      }
      else {
        observer.onCompleted()
      }
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
              case Continue =>
                try observer.onNext(elem) catch {
                  case NonFatal(ex) =>
                    observer.onError(ex)
                }
              case Done => Done
            }

            subscribers(observer) = f

            f.unsafeOnComplete {
              case Failure(_) | Success(Done) =>
                self.synchronized(subscribers.remove(observer))
                completeCountdown()
              case Success(Continue) =>
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

  def onError(ex: Throwable): Future[Done] = self.synchronized {
    if (!isDone) {
      isDone = true
      errorThrown = ex

      if (subscribers.nonEmpty) {
        val counter = Atomic(subscribers.size)
        val p = Promise[Done]()

        def completeCountdown(): Unit =
          if (counter.decrementAndGet() == 0) p.success(Done)

        for ((observer, ack) <- subscribers)
          ack.unsafeOnComplete {
            case Success(Continue) =>
              observer.onError(ex).onComplete {
                case _ => completeCountdown()
              }
            case Success(Done) | Failure(_) =>
              completeCountdown()
          }

        subscribers.clear()
        p.future
      }
      else
        Done
    }
    else
      Done
  }

  def onCompleted(): Future[Done] = self.synchronized {
    if (!isDone) {
      isDone = true

      if (subscribers.nonEmpty) {
        val counter = Atomic(subscribers.size)
        val p = Promise[Done]()

        def completeCountdown(): Unit =
          if (counter.decrementAndGet() == 0) p.success(Done)

        for ((observer, ack) <- subscribers)
          ack.unsafeOnComplete {
            case Success(Continue) =>
              observer.onCompleted().onComplete {
                case _ => completeCountdown()
              }
            case Success(Done) | Failure(_) =>
              completeCountdown()
          }

        subscribers.clear()
        p.future
      }
      else
        Done
    }
    else
      Done
  }
}

object BehaviorSubject {
  def apply[T](initialValue: T)(implicit s: Scheduler): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue, s)
}
