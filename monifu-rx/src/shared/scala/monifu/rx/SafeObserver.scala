package monifu.rx

import monifu.concurrent.Cancelable
import monifu.concurrent.locks.ReadWriteLock

final class SafeObserver[-T] private (observer: Observer[T], cancelable: Cancelable) extends Observer[T] {
  private[this] val lock = ReadWriteLock()
  private[this] var isDone = false

  def onNext(elem: T): Unit =
    lock.readLock {
      if (!isDone)
        observer.onNext(elem)
    }

  def onError(ex: Throwable): Unit =
    lock.writeLock {
      if (!isDone)
        try observer.onError(ex) finally {
          isDone = true
          cancelable.cancel()
        }
    }

  def onCompleted(): Unit =
    lock.writeLock {
      if (!isDone)
        try observer.onCompleted() finally {
          isDone = true
          cancelable.cancel()
        }
    }
}

object SafeObserver {
  def apply[T](next: T => Unit, error: Throwable => Unit, complete: () => Unit, cancelable: Cancelable): Observer[T] = {
    val o = new Observer[T] {
      def onNext(e: T) = next(e)
      def onError(ex: Throwable) = error(ex)
      def onCompleted() = complete()
    }

    new SafeObserver[T](o, cancelable)
  }

  def apply[T](observer: Observer[T], cancelable: Cancelable): Observer[T] =
    new SafeObserver(observer, cancelable)
}
