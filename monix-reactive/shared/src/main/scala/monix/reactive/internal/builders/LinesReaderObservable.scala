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

import java.io.{BufferedReader, Reader}

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal
import monix.reactive.exceptions.MultipleSubscribersException

import scala.annotation.tailrec
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success}

private[reactive] final class LinesReaderObservable(reader: Reader)
  extends Observable[String] { self =>

  private[this] val in: BufferedReader =
    if (!reader.isInstanceOf[BufferedReader])
      new BufferedReader(reader)
    else
      reader.asInstanceOf[BufferedReader]

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[String]): Cancelable = {
    if (wasSubscribed.getAndSet(true)) {
      out.onError(MultipleSubscribersException.build("LinesReaderObservable"))
      Cancelable.empty
    }
    else {
      // A token that will be checked for cancellation
      val cancelable = BooleanCancelable()
      val em = out.scheduler.executionModel
      // Schedule first cycle
      if (em.isAlwaysAsync)
        reschedule(Continue, out, cancelable, em)(out.scheduler)
      else
        fastLoop(out, cancelable, em, 0)(out.scheduler)

      cancelable
    }
  }

  private def reschedule(ack: Future[Ack], out: Subscriber[String],
    c: BooleanCancelable, em: ExecutionModel)(implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(next) =>
        // Should we continue, or should we close the stream?
        if (next == Continue && !c.isCanceled)
          fastLoop(out, c, em, 0)
        else
          triggerCancel(s)

      case Failure(ex) =>
        // This branch should never happen, but you never know.
        try s.reportFailure(ex)
        finally triggerCancel(s)
    }
  }

  @tailrec
  private def fastLoop(out: Subscriber[String], c: BooleanCancelable,
    em: ExecutionModel, syncIndex: Int)(implicit s: Scheduler): Unit = {

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
      val next = blocking(in.readLine())
      // We did our I/O, from now on we can no longer stream onError
      streamErrors = false

      ack = if (next != null) {
        // As long as the returned line is not null, it means
        // we haven't reached EOF.
        out.onNext(next)
      } else {
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
        fastLoop(out, c, em, nextIndex)
      else
        reschedule(ack, out, c, em)
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
