/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import java.io.{ BufferedReader, Reader }

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.exceptions.APIContractViolationException
import monix.execution.internal.Platform

import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.concurrent.{ blocking, Future }
import scala.util.{ Failure, Success }

private[reactive] final class LinesReaderObservable(reader: Reader) extends Observable[String] { self =>

  private[this] val in: BufferedReader =
    if (!reader.isInstanceOf[BufferedReader])
      new BufferedReader(reader)
    else
      reader.asInstanceOf[BufferedReader]

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[String]): Cancelable = {
    if (wasSubscribed.getAndSet(true)) {
      out.onError(APIContractViolationException("LinesReaderObservable does not support multiple subscribers"))
      Cancelable.empty
    } else {
      // A token that will be checked for cancellation
      val cancelable = BooleanCancelable()
      val em = out.scheduler.executionModel
      // Schedule first cycle
      reschedule(Continue, out, cancelable, em)(out.scheduler)

      cancelable
    }
  }

  private def reschedule(ack: Future[Ack], out: Subscriber[String], c: BooleanCancelable, em: ExecutionModel)(
    implicit s: Scheduler
  ): Unit = {

    ack.onComplete {
      case Success(next) =>
        // Should we continue, or should we close the stream?
        if (next == Continue && !c.isCanceled) {
          // Using Scala's BlockContext, since this is potentially a blocking call
          blocking(fastLoop(out, c, em, 0))
        }
      // else stop
      case Failure(ex) =>
        reportFailure(ex)
    }
  }

  @tailrec
  private def fastLoop(out: Subscriber[String], c: BooleanCancelable, em: ExecutionModel, syncIndex: Int)(
    implicit s: Scheduler
  ): Unit = {

    // Dealing with mutable status in order to keep the
    // loop tail-recursive :-(
    var errorThrown: Throwable = null
    var ack: Future[Ack] = Continue
    var streamErrors = true

    try {
      val next = in.readLine()
      // From this point on, whatever happens is a protocol violation
      streamErrors = false

      ack = if (next != null) {
        // As long as the returned line is not null, it means
        // we haven't reached EOF.
        out.onNext(next)
      } else {
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

      if (!c.isCanceled) {
        if (nextIndex > 0)
          fastLoop(out, c, em, nextIndex)
        else if (nextIndex >= 0)
          reschedule(ack, out, c, em)
      }
    } else {
      // Dealing with unexpected errors
      if (streamErrors)
        sendError(out, errorThrown)
      else
        reportFailure(errorThrown)
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
