package monifu.rx.observers

trait Observer[-T] {
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onCompleted(): Unit
}
