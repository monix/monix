package monifu.reactive.api

import monifu.reactive.Observer
import monifu.reactive.api.Ack.{Done, Continue}
import scala.concurrent.{Promise, Future}
import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.padded.Atomic
import scala.util.Success


final class BufferedObserver[-T] private (observer: Observer[T])(implicit scheduler: Scheduler) extends Observer[T] {
  private[this] val ack = Atomic(Continue : Future[Ack])

  def onNext(elem: T) = {
    val p = Promise[Ack]()
    val newAck = p.future
    val oldAck = ack.getAndSet(newAck)

    oldAck.onComplete {
      case Success(Continue) =>
        p.completeWith(observer.onNext(elem))
      case other =>
        p.complete(other)
    }

    newAck
  }

  def onError(ex: Throwable): Unit = {
    val oldAck = ack.getAndSet(Done)
    oldAck.onSuccess { case Continue => observer.onError(ex) }
  }

  def onComplete(): Unit = {
    val oldAck = ack.getAndSet(Done)
    oldAck.onSuccess { case Continue => observer.onComplete() }
  }
}

object BufferedObserver {
  def apply[T](observer: Observer[T])(implicit scheduler: Scheduler): BufferedObserver[T] =
    new BufferedObserver[T](observer)(scheduler)
}
