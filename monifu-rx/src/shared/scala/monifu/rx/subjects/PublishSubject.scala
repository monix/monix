package monifu.rx.subjects

import scala.concurrent.{Promise, Future}
import monifu.rx.api.Ack
import monifu.rx.api.Ack.{Continue, Done}
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.rx.Observer
import scala.collection.mutable
import monifu.concurrent.atomic.Atomic
import scala.util.{Success, Failure}

final class PublishSubject[T] private (s: Scheduler) extends Subject[T] { self =>
  protected implicit val scheduler = s
  private[this] val subscribers = mutable.Map.empty[Observer[T], Future[Ack]]
  private[this] var isDone = false

  def subscribe(observer: Observer[T]): Cancelable =
    self.synchronized {
      if (!isDone) {
        subscribers.update(observer, Continue)
        Cancelable {
          subscribers.remove(observer)
        }
      }
      else {
        observer.onCompleted()
        Cancelable.empty
      }
    }

  def onNext(elem: T): Future[Ack] = self.synchronized {
    if (!isDone) {
      val counter = Atomic(subscribers.size)
      val p = Promise[Continue]()

      def completeCountdown(): Unit =
        if (counter.decrementAndGet() == 0) p.success(Continue)

      for ((observer, ack) <- subscribers) {
        val f = ack match {
          case Continue =>
            observer.onNext(elem)
          case Done =>
            Done
          case other if other.isCompleted =>
            if (other.value.get.isSuccess && other.value.get.get == Continue)
              observer.onNext(elem)
            else
              Done
          case other =>
            ack.flatMap {
              case Done => Done
              case Continue => observer.onNext(elem)
            }
        }

        subscribers(observer) = f

        f.onComplete {
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
      Done
  }

  def onError(ex: Throwable): Future[Done] = self.synchronized {
    if (!isDone) {
      isDone = true

      if (subscribers.nonEmpty) {
        val counter = Atomic(subscribers.size)
        val p = Promise[Done]()

        def completeCountdown(): Unit =
          if (counter.decrementAndGet() == 0) p.success(Done)

        for ((observer, ack) <- subscribers)
          ack.onComplete {
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
          ack.onComplete {
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

object PublishSubject {
  def apply[T]()(implicit scheduler: Scheduler): PublishSubject[T] =
    new PublishSubject[T](scheduler)
}