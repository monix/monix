package monifu.rx.sync.observers

import monifu.concurrent.Cancelable
import scala.util.control.NonFatal
import monifu.rx.sync.Observer
import monifu.rx.Ack
import monifu.rx.Ack.{Continue, Stop}

/**
 * An observer wrapper that cancels its subscription on completed or on errors being thrown.
 */
final class AutoDetachObserver[-T] private (observer: Observer[T], subscription: Cancelable) extends Observer[T] {
  override def onNext(elem: T): Ack =
    try {
      observer.onNext(elem) match {
        case Stop =>
          subscription.cancel()
          Stop
        case Continue =>
          Continue
      }
    }
    catch {
      // safe-guarding against rogue observers for proper resource-cleanup
      case NonFatal(ex) =>
        subscription.cancel()
        throw ex
    }

  override def onError(ex: Throwable): Unit =
    try observer.onError(ex) finally {
      subscription.cancel()
    }

  override def onCompleted(): Unit = 
    try observer.onCompleted() finally {
      subscription.cancel()
    }
}

object AutoDetachObserver {
  def apply[T](underlying: Observer[T], subscription: Cancelable): AutoDetachObserver[T] =
    new AutoDetachObserver[T](underlying, subscription)
}
