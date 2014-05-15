package monifu.rx.sync.subjects

import monifu.concurrent.Cancelable
import collection.immutable.Set
import monifu.rx.sync.observers.Subscriber
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import scala.util.control.NonFatal
import monifu.rx.base.Ack.{Stop, Continue}
import monifu.rx.sync.Observer


final class PublishSubject[T] private () extends Subject[T] {
  private[this] val lock = new AnyRef
  private[this] var observers = Set.empty[Subscriber[T]]
  private[this] var isDone = false

  def subscribe(observer: Observer[T]): Cancelable =
    lock.synchronized {
      if (!isDone) {
        val sub = SingleAssignmentCancelable()
        val subscriber = Subscriber(observer, sub)
        observers = observers + subscriber
        sub := Cancelable { observers = observers - subscriber }
        sub
      }
      else
        Cancelable.empty
    }

  def onNext(elem: T) = lock.synchronized {
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

  def onError(ex: Throwable) = lock.synchronized {
    if (!isDone)
      try {
        observers.foreach(_.onError(ex))
      }
      finally {
        isDone = true
        observers = Set.empty
      }
  }

  def onCompleted() = lock.synchronized {
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