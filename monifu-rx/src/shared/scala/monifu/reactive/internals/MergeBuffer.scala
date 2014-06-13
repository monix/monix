package monifu.reactive.internals

import monifu.concurrent.atomic.Atomic
import monifu.reactive.api.BufferPolicy.{OverflowTriggering, Unbounded}
import monifu.reactive.{Observable, Observer}
import monifu.reactive.observers.{SynchronousBufferedObserver, SynchronousObserver, BufferedObserver}
import monifu.reactive.api.{Ack, BufferPolicy}
import scala.annotation.tailrec
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.concurrent.Scheduler
import scala.util.control.NonFatal
import monifu.concurrent.locks.SpinLock


private[monifu] final class BoundedMergeBuffer[U](downstream: Observer[U], mergeBatchSize: Int, bufferPolicy: BufferPolicy)(implicit scheduler: Scheduler)
  extends Observer[U] { self =>

  require(mergeBatchSize > 0, "mergeBatchSize must be strictly positive")

  private[this] val lock = SpinLock()
  private[this] val buffer = BufferedObserver(downstream, bufferPolicy)

  private[this] var permission = if (mergeBatchSize <= 0) null else Promise[Ack]()
  private[this] var activeStreams = 1
  private[this] var pendingStreams = 0
  private[this] var isDone = false

  def merge(upstream: Observable[U], wasPending: Boolean = false): Future[Ack] =
    lock.enter {
      if (isDone) {
        Cancel
      }
      else if (mergeBatchSize > 0 && activeStreams >= mergeBatchSize + 1) {
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

  private[this] def cancelStreaming(signalError: Throwable = null): Unit =
    lock.enter {
      if (!isDone) {
        isDone = true
        activeStreams = 0
        pendingStreams = 0

        if (mergeBatchSize > 0)
          permission.success(Cancel)

        if (signalError ne null)
          buffer.onError(signalError)
      }
    }

  def onNext(elem: U) = {
    buffer.onNext(elem).onCancel(cancelStreaming())
  }

  def onError(ex: Throwable) = {
    cancelStreaming(ex)
  }

  def onComplete() = lock.enter {
    if (!isDone) {
      if (activeStreams == 1 && pendingStreams == 0) {
        activeStreams = 0
        if (mergeBatchSize > 0)
          permission.success(Cancel)
        buffer.onComplete()
        isDone = true
      }
      else if (mergeBatchSize > 0 && activeStreams == mergeBatchSize + 1) {
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

private[monifu] final class UnboundedMergeBuffer[U](downstream: Observer[U], bufferPolicy: BufferPolicy)(implicit scheduler: Scheduler)
  extends SynchronousObserver[U] { self =>

  private[this] val activeStreams = Atomic(1)
  private[this] val buffer = bufferPolicy match {
    case Unbounded =>
      SynchronousBufferedObserver.unbounded(downstream)
    case OverflowTriggering(bufferSize) =>
      SynchronousBufferedObserver.overflowTriggering(downstream, bufferSize)
    case _ =>
      throw new IllegalArgumentException(bufferPolicy.toString)
  }

  @tailrec
  def merge(upstream: Observable[U]): Ack =
    activeStreams.get match {
      case current if current > 0 =>
        if (!activeStreams.compareAndSet(current, current + 1))
          merge(upstream)
        else try {
          upstream.unsafeSubscribe(self)
          Continue
        }
        catch {
          case NonFatal(ex) =>
            self.onError(ex)
            Cancel
        }

      case _ =>
        // we are done, so we must cancel upstream
        Cancel
    }

  private[this] def cancelStreamingNow(signalError: Throwable = null): Unit = {
    val current = activeStreams.get
    if (current > 0) {
      if (!activeStreams.compareAndSet(current, 0))
        cancelStreamingNow(signalError)
      else if (signalError ne null)
        buffer.onError(signalError)
    }
  }

  def onNext(elem: U) =
    buffer.onNext(elem) match {
      case Continue => Continue
      case Cancel =>
        cancelStreamingNow()
        Cancel
    }

  def onError(ex: Throwable) = {
    cancelStreamingNow(ex)
  }

  @tailrec
  def onComplete() = {
    activeStreams.get match {
      case current if current == 1 =>
        if (!activeStreams.compareAndSet(current, 0))
          onComplete()
        else
          buffer.onComplete()

      case current if current > 0 =>
        if (!activeStreams.compareAndSet(current, current - 1))
          onComplete()

      case _ =>
        // do nothing else
    }
  }
}
