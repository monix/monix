package monifu.rx.observers

import monifu.concurrent.Cancelable
import monifu.concurrent.cancelables.CompositeCancelable

final case class Subscriber[-T](private val observer: Observer[T], private val subscription: CompositeCancelable)
  extends Observer[T] with Cancelable {

  def onNext(elem: T): Unit = observer.onNext(elem)
  def onError(ex: Throwable): Unit = observer.onError(ex)
  def onCompleted(): Unit = observer.onCompleted()

  def cancel(): Unit = subscription.cancel()
  def isCanceled: Boolean = subscription.isCanceled

  def map[U](f: Observer[T] => Observer[U]): Subscriber[U] =
    copy(observer = f(observer))

  def add(subscription: Cancelable): Cancelable = {
    this.subscription += subscription
    this.subscription
  }
}

