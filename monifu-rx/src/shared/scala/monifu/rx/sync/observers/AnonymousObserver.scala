package monifu.rx.sync.observers

import monifu.rx.sync.Observer
import monifu.rx.base.{OnErrorRuntimeException, Ack}
import Ack.Continue

final class AnonymousObserver[-T] private (nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit)
  extends Observer[T] {

  def onNext(elem: T): Ack = {
    nextFn(elem)
    Continue
  }

  def onError(ex: Throwable): Unit =
    errorFn(ex)

  def onCompleted(): Unit =
    completedFn()
}

object AnonymousObserver {
  def apply[T](nextFn: T => Unit, errorFn: Throwable => Unit, completedFn: () => Unit): Observer[T] =
    new AnonymousObserver[T](nextFn, errorFn, completedFn)

  def apply[T](nextFn: T => Unit, errorFn: Throwable => Unit): Observer[T] =
    new AnonymousObserver[T](nextFn, errorFn, () => ())

  def apply[T](nextFn: T => Unit): Observer[T] =
    new AnonymousObserver[T](
      nextFn = nextFn,
      completedFn = () => (),
      errorFn = (ex: Throwable) =>
        throw new OnErrorRuntimeException(ex)
    )
}
