package monifu.rx.api

import Ack.{Continue, Stop}
import monifu.rx.Observer

/**
 * An observer wrapper that ensures the Rx grammar for onComplete/onError is respected,
 * according to the guidelines at: http://go.microsoft.com/fwlink/?LinkID=205219
 *
 * Messages sent to Observer instances must follow this grammar:
 * {{{
 *      onNext* (onCompleted | onError)?
 * }}}
 *
 * In other words, once onCompleted or onError happens, then the Observer
 * shouldn't receive any more onNext messages.
 *
 * This wrapper does NOT protect against multiple onNext messages being
 * sent concurrently. Synchronization at that level is the responsibility
 * of the Observer implementation given to `subscribe()`.
 */
final class SynchronizedObserver[-T] private (underlying: Observer[T]) extends Observer[T] {
  private[this] val lock = new AnyRef
  private[this] var isDone = false

  def onNext(elem: T): Ack  =
    lock.synchronized {
      if (!isDone)
        underlying.onNext(elem) match {
          case Continue =>
            Continue
          case Stop =>
            isDone = true
            Stop
        }
      else
        Stop
    }

  def onError(ex: Throwable): Unit =
    lock.synchronized {
      if (!isDone)
        try underlying.onError(ex) finally {
          isDone = true
        }
    }

  def onCompleted(): Unit =
    lock.synchronized {
      if (!isDone)
        try underlying.onCompleted() finally {
          isDone = true
        }
    }
}

object SynchronizedObserver {
  def apply[T](observer: Observer[T]): SynchronizedObserver[T] =
    observer match {
      case ref: SynchronizedObserver[_] =>
        ref.asInstanceOf[SynchronizedObserver[T]]
      case _ =>
        new SynchronizedObserver[T](observer)
    }
}
