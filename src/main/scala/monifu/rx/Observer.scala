package monifu.rx

import annotation.tailrec
import monifu.concurrent.atomic.Atomic
import scala.util.control.NonFatal

trait Observer[-T] {
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onCompleted(): Unit
}

final class SafeObserver[-T] private (observer: Observer[T], subscription: Subscription) extends Observer[T] {
  private[this] val IDLE = 0
  private[this] val NEXT = 1
  private[this] val DONE = 2

  @volatile
  private[this] var writeBarrier = false
  private[this] val state = Atomic(IDLE)

  @tailrec
  def onNext(elem: T) = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, NEXT)) {
        val err = try {
          observer.onNext(elem)
          None
        }
        catch {
          case NonFatal(ex) =>
            Some(ex)
        }
        finally {
          state.set(IDLE)
        }

        if (err != None)
          onError(err.get)
      }
      else
        onNext(elem)
    case NEXT =>
      onNext(elem)
    case DONE =>
      // do nothing
  }

  @tailrec
  def onError(ex: Throwable) = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, DONE))
        try {
          observer.onError(ex)
          writeBarrier = true
        }
        finally {
          subscription.unsubscribe()
        }
      else
        // retry
        onError(ex)
    case NEXT =>
      // retry
      onError(ex)
    case DONE =>
      // do nothing
  }

  @tailrec
  def onCompleted() = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, DONE))
        try {
          observer.onCompleted()
          writeBarrier = true
        }
        finally {
          subscription.unsubscribe()
        }
      else
        // retry
        onCompleted()
    case NEXT =>
      // retry
      onCompleted()
    case DONE =>
      // do nothing
  }
}

object SafeObserver {
  def apply[T](next: T => Unit, error: Throwable => Unit, complete: () => Unit, subscription: Subscription): Observer[T] = {
    val o = new Observer[T] {
      def onNext(e: T) = next(e)
      def onError(ex: Throwable) = error(ex)
      def onCompleted() = complete()
    }

    new SafeObserver[T](o, subscription)
  }

  def apply[T](observer: Observer[T], subscription: Subscription): Observer[T] =
    new SafeObserver(observer, subscription)
}
