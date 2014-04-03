package monifu.rx

import monifu.concurrent.Cancelable

final case class Subscriber[-T](observer: Observer[T], subscription: Cancelable)
  extends Observer[T] with Cancelable {

  def onNext(elem: T) = observer.onNext(elem)
  def onError(ex: Throwable) = observer.onError(ex)
  def onCompleted() = observer.onCompleted()

  def cancel() = subscription.cancel()
  def isCanceled = subscription.isCanceled
}
