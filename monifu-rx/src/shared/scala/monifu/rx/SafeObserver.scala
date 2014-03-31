package monifu.rx

import monifu.concurrent.Cancelable
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec


final class SafeObserver[-T] private (observer: Observer[T], cancelable: Cancelable) extends Observer[T] {
  private[this] val IDLE = 0
  private[this] val NEXT = 1
  private[this] val DONE = 2

  @volatile
  private[this] var writeBarrier = false
  private[this] val state = Atomic(IDLE)

  @tailrec
  def onNext(elem: T) = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, NEXT))
        try {
          observer.onNext(elem)
        }
        finally {
          state.set(IDLE)
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
          writeBarrier = !writeBarrier
        }
        finally {
          cancelable.cancel()
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
          writeBarrier = !writeBarrier
        }
        finally {
          cancelable.cancel()
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
