package monifu.rx.sync.observers

import monifu.rx.sync.Observer
import monifu.concurrent.Cancelable
import monifu.rx.base.Ack
import monifu.rx.base.Ack.{Continue, Stop}
import monifu.concurrent.cancelables.BooleanCancelable

final case class Subscriber[-T](observer: Observer[T], subscription: BooleanCancelable)
  extends Observer[T] with Cancelable {

  def onNext(elem: T): Ack =
    observer.onNext(elem) match {
      case Stop =>
        subscription.cancel()
        Stop
      case Continue =>
        Continue
    }

  def onError(ex: Throwable): Unit =
    try observer.onError(ex) finally subscription.cancel()

  def onCompleted(): Unit =
    try observer.onCompleted() finally subscription.cancel()

  def isCanceled: Boolean =
    subscription.isCanceled

  def cancel(): Unit =
    subscription.cancel()
}
