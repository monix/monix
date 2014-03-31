package monifu.rx.subjects

import monifu.rx.{Observable, Observer}
import monifu.concurrent.Cancelable
import monifu.concurrent.locks.ReadWriteLock
import monifu.concurrent.cancelables.{CompositeCancelable, BooleanCancelable}
import collection.immutable.Set

final class PublishSubject[T] private () extends Observable[T] with Observer[T] {
  private[this] val lock = ReadWriteLock()
  private[this] var observers = Set.empty[Observer[T]]
  private[this] val composite = CompositeCancelable()
  private[this] var isDone = false

  protected def fn(observer: Observer[T]): Cancelable =
    lock.writeLock {
      if (!isDone) {
        observers = observers + observer
        val sub = BooleanCancelable {
          observers = observers - observer
        }

        composite += sub
        sub
      }
      else
        BooleanCancelable.alreadyCanceled
    }

  def onNext(elem: T): Unit = lock.readLock {
    if (!isDone)
      observers.foreach(_.onNext(elem))
  }

  def onError(ex: Throwable): Unit = lock.writeLock {
    if (!isDone)
      try {
        observers.foreach(_.onError(ex))
      }
      finally {
        isDone = true
        composite.cancel()
      }
  }

  def onCompleted(): Unit = lock.writeLock {
    if (!isDone)
      try {
        observers.foreach(_.onCompleted())
      }
      finally {
        isDone = true
        composite.cancel()
      }
  }
}

object PublishSubject {
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]
}