package monifu.rx.sync.subjects

import monifu.concurrent.Cancelable
import monifu.concurrent.locks.NaiveReadWriteLock
import collection.immutable.Set
import monifu.rx.sync.{Observer, Observable}
import monifu.rx.sync.observers.Subscriber
import monifu.rx.common.Ack
import Ack.{Continue, Stop}
import monifu.rx.common.Ack
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import scala.util.control.NonFatal

final class PublishSubject[T] private () extends Observable[T] with Observer[T] {
  private[this] val lock = NaiveReadWriteLock()
  private[this] var observers = Set.empty[Subscriber[T]]
  private[this] var isDone = false

  def subscribe(observer: Observer[T]): Cancelable =
    lock.writeLock {
      if (!isDone) {
        val sub = SingleAssignmentCancelable()
        val subscriber = Subscriber(observer, sub)
        observers = observers + subscriber
        sub := Cancelable { observers = observers - subscriber }
        sub
      }
      else
        Cancelable.alreadyCanceled
    }

  def onNext(elem: T): Ack = lock.readLock {
    if (!isDone) {
      for (obs <- observers)
        try
          obs.onNext(elem)
        catch {
          case NonFatal(ex) =>
            obs.cancel()
            throw ex
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
        for (obs <- observers)
          try obs.onCompleted() finally obs.cancel()
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