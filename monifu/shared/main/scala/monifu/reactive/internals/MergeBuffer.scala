/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive.internals

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.{OverflowTriggering, Unbounded}
import monifu.reactive.observers.{BufferedObserver, SynchronousBufferedObserver, SynchronousObserver}
import monifu.reactive.{Ack, BufferPolicy, Observable, Observer}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal


private[monifu] final class BoundedMergeBuffer[U]
  (downstream: Observer[U], mergeBatchSize: Int, bufferPolicy: BufferPolicy)
      (implicit s: Scheduler) extends Observer[U] { self =>

  private[this] val lock = new AnyRef
  private[this] val buffer = BufferedObserver(downstream, bufferPolicy)

  private[this] var permission = if (mergeBatchSize <= 0) null else Promise[Ack]()
  private[this] var activeStreams = 1
  private[this] var pendingStreams = 0
  private[this] var isDone = false

  def merge(upstream: Observable[U], wasPending: Boolean = false): Future[Ack] =
    lock.synchronized {
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
    lock.synchronized {
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

  def onComplete() = lock.synchronized {
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

private[monifu] final class UnboundedMergeBuffer[U]
  (downstream: Observer[U], bufferPolicy: BufferPolicy)
      (implicit ec: ExecutionContext) extends SynchronousObserver[U] { self =>

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
