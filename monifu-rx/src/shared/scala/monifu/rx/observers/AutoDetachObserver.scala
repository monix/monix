package monifu.rx.observers

import monifu.concurrent.Cancelable
import scala.util.control.NonFatal
import monifu.rx.Observer

/**
 * An observer wrapper that cancels its subscription on completed or on errors being thrown.
 */
final class AutoDetachObserver[-T] private (underlying: Observer[T], subscription: Cancelable) extends Observer[T] {
  def onNext(elem: T): Unit =
    try {
      underlying.onNext(elem)
    }
    catch {
      // safe-guarding against rogue observers for proper resource-cleanup
      case NonFatal(ex) =>
        subscription.cancel()
        throw ex
    }

  def onError(ex: Throwable): Unit =
    try underlying.onError(ex) finally {
      subscription.cancel()
    }

  def onCompleted(): Unit =
    try underlying.onCompleted() finally {
      subscription.cancel()
    }
}

object AutoDetachObserver {
  def apply[T](underlying: Observer[T], subscription: Cancelable): AutoDetachObserver[T] =
    new AutoDetachObserver[T](underlying, subscription)
}
