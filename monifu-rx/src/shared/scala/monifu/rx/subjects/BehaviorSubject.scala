package monifu.rx.subjects

import monifu.rx.{Observable, Observer}
import monifu.concurrent.Cancelable
import monifu.concurrent.locks.Lock
import monifu.concurrent.cancelables.BooleanCancelable
import collection.immutable.Set
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal


final class BehaviorSubject[T] private () extends Observable[T] with Observer[T] {
  private[this] val lock = Lock()
  private[this] var observers = Set.empty[Observer[T]]
  private[this] var isDone = false
  private[this] var lastValue = Option.empty[Try[T]]

  protected def fn(observer: Observer[T]): Cancelable =
    lock.acquire {
      if (!isDone) {
        for (l <- lastValue; v <- l)
          observer.onNext(v)

        observers = observers + observer
        BooleanCancelable {
          observers = observers - observer
        }
      }
      else {
        for (l <- lastValue; ex <- l.failed)
          observer.onError(ex)
        BooleanCancelable.alreadyCanceled
      }
    }

  def onNext(elem: T): Unit =
    lock.acquire {
      if (!isDone) {
        var err: Throwable = null
        lastValue = Some(Success(elem))
        for (obs <- observers)
          try {
            obs.onNext(elem)
          }
          catch {
            case NonFatal(e) => err = e
          }

        // in case an error happened, throw the first one
        if (err != null) throw err
      }
    }

  def onError(ex: Throwable): Unit =
    lock.acquire {
      if (!isDone) {
        var err: Throwable = null
        isDone = true
        lastValue = Some(Failure(ex))

        for (obs <- observers)
          try {
            obs.onError(ex)
          }
          catch {
            case NonFatal(e) => err = e
          }

        observers = Set.empty
        // in case errors happened, throw the first one
        if (err != null) throw err
      }
    }

  def onCompleted(): Unit =
    lock.acquire {
      if (!isDone) {
        var err: Throwable = null
        isDone = true

        for (obs <- observers)
          try {
            obs.onCompleted()
          }
          catch {
            case NonFatal(e) => err = e
          }

        observers = Set.empty
        // in case errors happened, throw the first one
        if (err != null) throw err
      }
    }
}

object BehaviorSubject {
  def apply[T](): BehaviorSubject[T] =
    new BehaviorSubject[T]
}