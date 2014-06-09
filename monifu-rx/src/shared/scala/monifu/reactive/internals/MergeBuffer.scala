package monifu.reactive.internals

import monifu.reactive.{Observable, Observer}
import monifu.reactive.observers.BufferedObserver
import monifu.reactive.api.{Ack, BufferPolicy}
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.concurrent.Scheduler
import scala.util.control.NonFatal
import monifu.concurrent.locks.SpinLock


final class MergeBuffer[U](downstream: Observer[U], bufferPolicy: BufferPolicy)(implicit scheduler: Scheduler)
  extends Observer[U] { self =>

  import MergeBuffer.mergeBatchSize

  private[this] val buffer = BufferedObserver(downstream, bufferPolicy)

  private[this] val lock = SpinLock()
  private[this] var permission = Promise[Ack]()
  private[this] var activeStreams = 1
  private[this] var pendingStreams = 0
  private[this] var isDone = false

  def merge(upstream: Observable[U], wasPending: Boolean = false): Future[Ack] =
    lock.enter {
      if (isDone) {
        Cancel
      }
      else if (activeStreams >= mergeBatchSize) {
        if (!wasPending) pendingStreams += 1

        permission.future.flatMap {
          case Cancel => Cancel
          case Continue =>
            merge(upstream, wasPending = true)
        }
      }
      else {
        if (wasPending) pendingStreams -= 1
        activeStreams += 1

        try {
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

  private[this] def cancelStreaming(signalError: Throwable = null): Unit = {
    if (!isDone) {
      isDone = true
      activeStreams = 0
      pendingStreams = 0
      permission.success(Cancel)
      if (signalError ne null)
        buffer.onError(signalError)
    }
  }

  def onNext(elem: U) = lock.enter {
    buffer.onNext(elem).onCancel(cancelStreaming())
  }

  def onError(ex: Throwable) = lock.enter {
    cancelStreaming(ex)
  }

  def onComplete() = lock.enter {
    if (activeStreams > 0) {
      if (activeStreams == 1 && pendingStreams == 0) {
        activeStreams = 0
        permission.success(Cancel)
        buffer.onComplete()
        isDone = true
      }
      else if (activeStreams == mergeBatchSize) {
        permission.success(Continue)
        permission = Promise[Ack]()
        activeStreams -= 1
      }
      else if (activeStreams > 0) {
        activeStreams -= 1
      }
    }
  }
}

object MergeBuffer {
  final val mergeBatchSize = math.min(1024, math.max(1, Runtime.getRuntime.availableProcessors()) * 5)
}
