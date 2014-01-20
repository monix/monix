package monifu.rx

import annotation.tailrec
import monifu.concurrent.atomic.Atomic

trait Observer[-T] {
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onComplete(): Unit  
}

object Observer {
  def apply[T](next: T => Unit, error: Throwable => Unit, complete: () => Unit): Observer[T] =
    SafeObserver(next, error, complete)

  def apply[T](f: T => Unit): Observer[T] = 
    SafeObserver(f,
      (ex: Throwable) => throw ex,
      () => {}
    )
}

final class SafeObserver[-T] private (observer: Observer[T]) extends Observer[T] {
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
        try (observer.onNext(elem)) finally {
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
      if (state.compareAndSet(IDLE, DONE)) {
        observer.onError(ex)
        writeBarrier = true
      }
      else
        onError(ex)
    case NEXT =>
      onError(ex)
    case DONE =>
      // do nothing
  }

  @tailrec
  def onComplete() = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, DONE)) {
        observer.onComplete()
        writeBarrier = true
      }
      else
        onComplete()
    case NEXT =>
      onComplete()
    case DONE =>
      // do nothing
  }
}

object SafeObserver {
  def apply[T](next: T => Unit, error: Throwable => Unit, complete: () => Unit): Observer[T] =
    new SafeObserver(new Observer[T] {
      def onNext(e: T) = next(e)
      def onError(ex: Throwable) = error(ex)
      def onComplete() = complete()
    })

  def apply[T](observer: Observer[T]): Observer[T] =
    if (!observer.isInstanceOf[SafeObserver[_]])
      new SafeObserver(observer)
    else
      observer

  def apply[T](f: T => Unit): Observer[T] = 
    SafeObserver(new Observer[T] {
      def onNext(e: T) = f(e)
      def onError(ex: Throwable) = throw ex
      def onComplete() = ()
    })
}
