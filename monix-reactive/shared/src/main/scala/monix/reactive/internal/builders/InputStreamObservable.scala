/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.reactive.internal.builders

import java.io.InputStream
import java.util

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.reactive.Observable
import monix.reactive.exceptions.MultipleSubscribersException
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal

import scala.annotation.tailrec
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success}

private[reactive] final class InputStreamObservable(
  in: InputStream,
  chunkSize: Int)
  extends Observable[Array[Byte]] { self =>

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[Array[Byte]]): Cancelable = {
    if (wasSubscribed.getAndSet(true)) {
      out.onError(MultipleSubscribersException.build("InputStreamObservable"))
      Cancelable.empty
    }
    else {
      val buffer = new Array[Byte](chunkSize)
      // A token that will be checked for cancellation
      val cancelable = BooleanCancelable()
      val em = out.scheduler.executionModel
      // Schedule first cycle
      if (em.isAlwaysAsync)
        reschedule(Continue, buffer, out, cancelable, em)(out.scheduler)
      else
        fastLoop(buffer, out, cancelable, em, 0)(out.scheduler)

      cancelable
    }
  }

  private def reschedule(ack: Future[Ack], b: Array[Byte], out: Subscriber[Array[Byte]],
    c: BooleanCancelable, em: ExecutionModel)(implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(next) =>
        // Should we continue, or should we close the stream?
        if (next == Continue && !c.isCanceled)
          fastLoop(b, out, c, em, 0)
        else
          triggerCancel(s)

      case Failure(ex) =>
        // This branch should never happen, but you never know.
        try s.reportFailure(ex)
        finally triggerCancel(s)
    }
  }

  @tailrec
  private def fastLoop(buffer: Array[Byte], out: Subscriber[Array[Byte]],
    c: BooleanCancelable, em: ExecutionModel, syncIndex: Int)(implicit s: Scheduler): Unit = {

    // Dealing with mutable status in order to keep the
    // loop tail-recursive :-(
    var errorThrown: Throwable = null
    var ack: Future[Ack] = Continue

    // Protects calls to user code from within the operator and
    // stream the error downstream if it happens, but if the
    // error happens because of calls to `onNext` or other
    // protocol calls, then we can just log it, but not stream it,
    // as we'd be breaching the protocol.
    var streamErrors = true

    try {
      // Using Scala's BlockContext, since this is potentially a blocking call
      val length = blocking(in.read(buffer))
      // We did our I/O, from now on we can no longer stream onError
      streamErrors = false

      ack = if (length >= 0) {
        // As long as the returned length is positive, it means
        // we haven't reached EOF. Making a copy of the array, because
        // we cannot send our mutable buffer.
        val next = util.Arrays.copyOf(buffer, length)
        out.onNext(next)
      } else { // length < 0
        // We have reached EOF, which means we need to close
        // the stream and send onComplete. But I/O errors can happen
        // and these we are allowed to stream.
        val ex =
          try { blocking(in.close()); null }
          catch { case NonFatal(err) => err }

        if (ex == null) out.onComplete()
        else out.onError(ex)
        Stop
      }
    } catch {
      case NonFatal(ex) =>
        errorThrown = ex
    }

    if (errorThrown == null) {
      // Logic for collapsing execution loops
      val nextIndex =
        if (ack == Continue) em.nextFrameIndex(syncIndex)
        else if (ack == Stop) -1
        else 0

      if (nextIndex < 0 || c.isCanceled)
        triggerCancel(s)
      else if (nextIndex > 0)
        fastLoop(buffer, out, c, em, nextIndex)
      else
        reschedule(ack, buffer, out, c, em)
    }
    else {
      // Dealing with unexpected errors
      try {
        if (streamErrors)
          out.onError(errorThrown)
        else
          s.reportFailure(errorThrown)
      } finally {
        triggerCancel(s)
      }
    }
  }

  private def triggerCancel(s: UncaughtExceptionReporter): Unit =
    try blocking(in.close()) catch {
      case NonFatal(ex) =>
        s.reportFailure(ex)
    }
}
