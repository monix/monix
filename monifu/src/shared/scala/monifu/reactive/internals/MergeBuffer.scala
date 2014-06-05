package monifu.reactive.internals

import monifu.reactive.{Observable, Observer}
import monifu.reactive.observers.BufferedObserver
import monifu.reactive.api.{Ack, BufferPolicy}
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.concurrent.Scheduler
import scala.util.control.NonFatal
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec


final class MergeBuffer[U](downstream: Observer[U], bufferPolicy: BufferPolicy)(implicit scheduler: Scheduler)
  extends Observer[U] { self =>

  import MergeBuffer.mergeBatchSize

  private[this] val buffer = BufferedObserver(downstream, bufferPolicy)
  private[this] final case class State(counter: Int, permission: Promise[Ack])
  private[this] val state = Atomic(State(counter = 1, permission = Promise()))

  @tailrec
  def merge(upstream: Observable[U]): Future[Ack] = {
    state.get match {
      case current @ State(counter, permission) =>
        if (counter == 0) {
          Cancel
        }
        else if (counter >= mergeBatchSize)
          permission.future.flatMap {
            case Continue =>
              retryMerge(upstream)
            case Cancel =>
              Cancel
          }
        else if (!state.compareAndSet(current, State(counter + 1, permission))) {
          merge(upstream)
        }
        else try {
          upstream.unsafeSubscribe(self)
          Continue
        }
        catch {
          case NonFatal(ex) =>
            self.onError(ex)
            Cancel
        }
    }
  }

  /**
   * Helper used in merge for forcing a non-tail-recursive call.
   */
  private[this] def retryMerge(upstream: Observable[U]) = {
    merge(upstream)
  }

  @tailrec
  private[this] def cancelStreaming(signalError: Throwable = null): Unit =
    state.get match {
      case current @ State(counter, permission) if counter > 0 =>
        if (!state.compareAndSet(current, State(counter = 0, permission)))
          cancelStreaming()
        else {
          permission.success(Cancel)
          if (signalError ne null)
            buffer.onError(signalError)
        }

      case _ => // already canceled
    }

  def onNext(elem: U) = {
    buffer.onNext(elem).onCancel(cancelStreaming())
  }

  def onError(ex: Throwable) = {
    cancelStreaming(signalError = ex)
  }

  @tailrec
  def onComplete() = state.get match {
    case current @ State(counter, permission) if counter > 0 =>
      if (counter - 1 == 0)
        if (!state.compareAndSet(current, State(0, permission)))
          onComplete()
        else {
          permission.success(Cancel)
          buffer.onComplete()
        }
      else if (counter == mergeBatchSize) {
        val newPromise = Promise[Ack]()
        if (!state.compareAndSet(current, State(counter - 1, newPromise)))
          onComplete()
        else
          permission.success(Continue)
      }
      else if (!state.compareAndSet(current, State(counter - 1, permission)))
        onComplete()

    case _ => // already complete
  }
}

object MergeBuffer {
  final val mergeBatchSize = math.min(1024, math.max(1, Runtime.getRuntime.availableProcessors()) * 32)
}
