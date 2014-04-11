package monifu.rx.sync.subjects

import monifu.concurrent.Cancelable
import monifu.concurrent.locks.NaiveReadWriteLock
import collection.immutable.Set
import monifu.rx.sync.{Observer, Observable}
import monifu.rx.Ack
import monifu.rx.Ack.{Continue, Stop}

final class PublishSubject[T] private () extends Observable[T] with Observer[T] {
  private[this] val lock = NaiveReadWriteLock()
  private[this] var observers = Set.empty[Observer[T]]
  private[this] var isDone = false

  protected def subscribeFn(observer: Observer[T]): Cancelable =
    lock.writeLock {
      if (!isDone) {
        observers = observers + observer
        Cancelable {
          observers = observers - observer
        }
      }
      else
        Cancelable.alreadyCanceled
    }

  def onNext(elem: T): Ack = lock.readLock {
    if (!isDone) {
      for (obs <- observers)
        obs.onNext(elem) match {
          case Continue => // nothing
          case Stop =>
            lock.writeLock { observers = observers - obs }
        }

      Continue
    }
    else
      Stop
  }

  def onError(ex: Throwable): Unit = lock.writeLock {
    if (!isDone)
      try {
        observers.foreach(_.onError(ex))
      }
      finally {
        isDone = true
        observers = Set.empty
      }
  }

  def onCompleted(): Unit = lock.writeLock {
    if (!isDone)
      try {
        observers.foreach(_.onCompleted())
      }
      finally {
        isDone = true
        observers = Set.empty
      }
  }
}

object PublishSubject {
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]
}