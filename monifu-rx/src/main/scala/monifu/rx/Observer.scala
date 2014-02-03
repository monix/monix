package monifu.rx

import annotation.tailrec
import monifu.concurrent.atomic.Atomic
import scala.util.control.NonFatal

trait Observer[-T] {
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onCompleted(): Unit
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
  def onCompleted() = state.get match {
    case IDLE =>
      if (state.compareAndSet(IDLE, DONE)) {
        observer.onCompleted()
        writeBarrier = true
      }
      else
        onCompleted()
    case NEXT =>
      onCompleted()
    case DONE =>
      // do nothing
  }
}

object SafeObserver {
  def apply[T](next: T => Unit, error: Throwable => Unit, complete: () => Unit): Observer[T] =
    new SafeObserver(new Observer[T] {
      def onNext(e: T) = next(e)
      def onError(ex: Throwable) = error(ex)
      def onCompleted() = complete()
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
      def onCompleted() = ()
    })
}
