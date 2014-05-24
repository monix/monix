package monifu.reactive.api

import monifu.reactive.Observer
import monifu.reactive.api.Ack.Done

/**
 * Wraps an observer into an observer with synchronized `onNext`, `onError` and `onComplete`,
 * addressing visibility and atomicity concerns for bad behaved observers or observables.
 */
final class SynchronizedObserver[-T](observer: Observer[T]) extends Observer[T] {
  private[this] val gate = new AnyRef
  private[this] var isDone = false
  
  def onNext(elem: T) =
    gate.synchronized {
      if (!isDone) observer.onNext(elem)
      else Done
    }

  def onComplete() =
    gate.synchronized {
      if (!isDone) {
        isDone = true
        observer.onComplete()
      }
      else
        Done
    }

  def onError(ex: Throwable) =
    gate.synchronized {
      if (!isDone) {
        isDone = true
        observer.onError(ex)
      }
      else
        Done
    }
}
