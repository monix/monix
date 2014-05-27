package monifu.reactive.observers

import monifu.reactive.Observer
import scala.concurrent.Future
import scala.util.control.NonFatal
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.api.Ack

/**
 * A safe observer ensures too things:
 *
 * - errors triggered by downstream observers are caught and streamed to `onError`,
 *   while the upstream gets an `Ack.Done`, to stop sending events
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
        if (result == Continue || result == Done || (result.isCompleted && result.value.get.isSuccess))
          result
        else
          result.recoverWith {
            case err =>
              onError(err)
              Done
          }
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Done
      }
    }
    else
      Done
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
          Done
      }
    }
    else
      Done
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
