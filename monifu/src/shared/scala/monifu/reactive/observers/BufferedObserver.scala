package monifu.reactive.observers

import monifu.reactive.Observer
import monifu.reactive.api.Ack.{Done, Continue}
import scala.concurrent.{Promise, Future}
import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.padded.Atomic
import scala.util.Success
import monifu.reactive.api.Ack
import monifu.reactive.internals.FutureAckExtensions


/**
 * An observer wrapper that ensures the underlying implementation does not
 * receive concurrent onNext / onError / onComplete events - for those
 * cases in which the producer is emitting data too fast or concurrently
 * without fulfilling the back-pressure requirements.
 */
final class BufferedObserver[-T] private (observer: Observer[T])(implicit scheduler: Scheduler) extends Observer[T] {
  private[this] val ack = Atomic(Continue : Future[Ack])

  def onNext(elem: T) = {
    val p = Promise[Ack]()
    val newAck = p.future
    val oldAck = ack.getAndSet(newAck)

    oldAck.onCompleteNow {
      case Success(Continue) =>
        observer.onNext(elem).onCompleteNow(r => p.complete(r))
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
    observer match {
      case ref: BufferedObserver[_] => ref.asInstanceOf[BufferedObserver[T]]
      case _ => new BufferedObserver[T](observer)
    }
}
