package monifu.reactive.observers

import monifu.reactive.Observer
import scala.concurrent.Future
import scala.util.control.NonFatal
import monifu.reactive.api.Ack.{Continue, Cancel}
import monifu.concurrent.Scheduler
import monifu.reactive.api.Ack

/**
 * A safe observer ensures too things:
 *
 * - errors triggered by downstream observers are caught and streamed to `onError`,
 *   while the upstream gets an `Ack.Cancel`, to stop sending events
 *
 * - once an `onError` or `onComplete` was emitted, the observer no longer accepts
 *   `onNext` events, ensuring that the Rx grammar is respected.
 *
 * This implementation doesn't address multi-threading concerns in any way.
 */
final class SafeObserver[-T] private (observer: Observer[T])(implicit scheduler: Scheduler)
  extends Observer[T] {

  private[this] var isDone = false

  def onNext(elem: T): Future[Ack] = {
    if (!isDone) {
      try {
        val result = observer.onNext(elem)
        if (result == Continue || result == Cancel || (result.isCompleted && result.value.get.isSuccess))
          result
        else
          result.recoverWith {
            case err =>
              onError(err)
              Cancel
          }
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Cancel
      }
    }
    else
      Cancel
  }

  def onError(ex: Throwable) = {
    if (!isDone) {
      isDone = true
      try observer.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }
    }
  }

  def onComplete() = {
    if (!isDone) {
      isDone = true
      try observer.onComplete() catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
          Cancel
      }
    }
    else
      Cancel
  }
}

object SafeObserver {
  /**
   * Wraps an Observer instance into a SafeObserver.
   */
  def apply[T](observer: Observer[T])(implicit scheduler: Scheduler): SafeObserver[T] =
    observer match {
      case ref: SafeObserver[_] => ref.asInstanceOf[SafeObserver[T]]
      case _ => new SafeObserver[T](observer)
    }
}
