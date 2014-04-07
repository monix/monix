package monifu.rx.subjects

import monifu.rx.Observable
import monifu.concurrent.Cancelable
import monifu.concurrent.locks.Lock
import collection.immutable.Set
import scala.util.{Failure, Success, Try}
import monifu.rx.observers.{Subscriber, Observer}


final class BehaviorSubject[T] private () extends Observable[T] with Observer[T] {
  private[this] val lock = Lock()
  private[this] var observers = Set.empty[Observer[T]]
  private[this] var isDone = false
  private[this] var lastValue = Option.empty[Try[T]]

  protected def subscribeFn(subscriber: Subscriber[T]): Cancelable =
    lock.acquire {
      if (!isDone) {
        for (l <- lastValue; v <- l)
          subscriber.onNext(v)

        observers = observers + subscriber
        Cancelable {
          observers = observers - subscriber
        }
      }
      else {
        for (l <- lastValue; ex <- l.failed)
          subscriber.onError(ex)
        Cancelable.alreadyCanceled
      }
    }

  def onNext(elem: T): Unit =
    lock.acquire {
      if (!isDone) {
        lastValue = Some(Success(elem))
        for (obs <- observers)
          obs.onNext(elem)
      }
    }

  def onError(ex: Throwable): Unit =
    lock.acquire {
      if (!isDone)
        try {
          for (obs <- observers)
            obs.onError(ex)
        }
        finally {
          isDone = true
          lastValue = Some(Failure(ex))
          observers = Set.empty
        }
    }

  def onCompleted(): Unit =
    lock.acquire {
      if (!isDone)
        try {
          for (obs <- observers)
            obs.onCompleted()
        }
        finally {
          isDone = true
          observers = Set.empty
        }
    }
}

object BehaviorSubject {
  def apply[T](): BehaviorSubject[T] =
    new BehaviorSubject[T]
}