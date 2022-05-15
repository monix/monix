/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.exceptions.APIContractViolationException
import monix.execution.internal.Platform

import scala.annotation.tailrec
import scala.concurrent.{ blocking, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

private[reactive] final class InputStreamObservable(in: InputStream, chunkSize: Int) extends Observable[Array[Byte]] {

  require(chunkSize > 0, "chunkSize > 0")

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[Array[Byte]]): Cancelable = {
    if (wasSubscribed.compareAndSet(expect = false, update = true)) {
      val buffer = new Array[Byte](chunkSize)
      // A token that will be checked for cancellation
      val cancelable = BooleanCancelable()
      val em = out.scheduler.executionModel
      // Schedule first cycle
      reschedule(Continue, buffer, out, cancelable, em)(out.scheduler)

      cancelable
    } else {
      out.onError(APIContractViolationException("InputStreamObservable does not support multiple subscribers"))
      Cancelable.empty
    }
  }

  private def reschedule(
    ack: Future[Ack],
    b: Array[Byte],
    out: Subscriber[Array[Byte]],
    c: BooleanCancelable,
    em: ExecutionModel
  )(implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(next) =>
        // Should we continue, or should we close the stream?
        if (next == Continue && !c.isCanceled) {
          // Using Scala's BlockContext, since this is potentially a blocking call
          blocking(fastLoop(b, out, c, em, 0))
        }
      // else stop
      case Failure(ex) =>
        reportFailure(ex)
    }
  }

  @tailrec
  private def fastLoop(
    buffer: Array[Byte],
    out: Subscriber[Array[Byte]],
    c: BooleanCancelable,
    em: ExecutionModel,
    syncIndex: Int
  )(implicit s: Scheduler): Unit = {

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
      val length = fillBuffer(in, buffer)
      // From this point on, whatever happens is a protocol violation
      streamErrors = false

      ack = if (length >= 0) {
        // As long as the returned length is positive, it means
        // we haven't reached EOF. Making a copy of the array, because
        // we cannot send our mutable buffer.
        val next = util.Arrays.copyOf(buffer, length)
        out.onNext(next)
      } else { // length < 0
        out.onComplete()
        Stop
      }
    } catch {
      case ex if NonFatal(ex) =>
        errorThrown = ex
    }

    if (errorThrown == null) {
      // Logic for collapsing execution loops
      val nextIndex =
        if (ack == Continue) em.nextFrameIndex(syncIndex)
        else if (ack == Stop) -1
        else 0

      if (nextIndex < 0 || c.isCanceled)
        ()
      else if (nextIndex > 0)
        fastLoop(buffer, out, c, em, nextIndex)
      else
        reschedule(ack, buffer, out, c, em)
    } else {
      // Dealing with unexpected errors
      if (streamErrors)
        sendError(out, errorThrown)
      else
        reportFailure(errorThrown)
    }
  }

  @tailrec
  private def fillBuffer(in: InputStream, buffer: Array[Byte], nTotalBytesRead: Int = 0): Int = {
    if (nTotalBytesRead >= buffer.length) nTotalBytesRead
    else {
      val nBytesRead = in.read(buffer, nTotalBytesRead, buffer.length - nTotalBytesRead)
      if (nBytesRead >= 0) fillBuffer(in, buffer, nTotalBytesRead + nBytesRead)
      else { // stream has ended
        if (nTotalBytesRead <= 0)
          nBytesRead // no more bytes (-1 via InputStream.read contract) available, end the observable
        else nTotalBytesRead // we read the last bytes available
      }
    }
  }

  private def sendError(out: Subscriber[Nothing], e: Throwable)(implicit s: UncaughtExceptionReporter): Unit = {
    try {
      out.onError(e)
    } catch {
      case NonFatal(e2) =>
        reportFailure(Platform.composeErrors(e, e2))
    }
  }

  private def reportFailure(e: Throwable)(implicit s: UncaughtExceptionReporter): Unit = {
    s.reportFailure(e)
    // Forcefully close in case of protocol violations, because we are
    // not signaling the error downstream, which could lead to leaks
    try in.close()
    catch { case NonFatal(_) => () }
  }
}
