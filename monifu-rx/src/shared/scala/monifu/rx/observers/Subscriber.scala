package monifu.rx.observers

import monifu.concurrent.Cancelable
import monifu.concurrent.cancelables.CompositeCancelable

final class Subscriber[-T] private (observer: Observer[T], private val subscription: CompositeCancelable)
  extends Observer[T] with CompositeCancelable {

  def onNext(elem: T): Unit = observer.onNext(elem)
  def onError(ex: Throwable): Unit = observer.onError(ex)
  def onCompleted(): Unit = observer.onCompleted()

  def isCanceled: Boolean = subscription.isCanceled
  def cancel(): Unit = subscription.cancel()
  def add(s: Cancelable): Unit = subscription.add(s)
  def remove(s: Cancelable): Unit = subscription.remove(s)

  def map[U](f: Observer[T] => Observer[U]): Subscriber[U] =
    new Subscriber[U](f(observer), subscription)
}

object Subscriber {
  def apply[T](observer: Observer[T], subscription: CompositeCancelable = CompositeCancelable()): Subscriber[T] =
    new Subscriber[T](observer, subscription)
}

