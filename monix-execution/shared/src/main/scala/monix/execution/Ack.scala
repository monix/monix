/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution

import monix.execution.misc.NonFatal
import scala.concurrent.duration.Duration
import scala.concurrent.{CanAwait, ExecutionContext, Future, Promise}
import scala.language.experimental.macros
import scala.util.{Failure, Success, Try}

/** Represents an acknowledgement of processing that a consumer
  * sends back upstream. Useful to implement back-pressure.
  */
sealed abstract class Ack extends Future[Ack] with Serializable {
  val AsSuccess: Success[Ack]

  // For Scala 2.12 compatibility
  final def transform[S](f: (Try[Ack]) => Try[S])(implicit executor: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    onComplete(r => p.complete(try f(r) catch { case NonFatal(t) => Failure(t) }))
    p.future
  }

  // For Scala 2.12 compatibility
  final def transformWith[S](f: (Try[Ack]) => Future[S])(implicit executor: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    onComplete(r => p.completeWith(try f(r) catch { case NonFatal(t) => Future.failed(t) }))
    p.future
  }

  final def onComplete[U](func: Try[Ack] => U)(implicit executor: ExecutionContext): Unit =
    executor.execute(new Runnable {
      def run(): Unit = func(AsSuccess)
    })
}

object Ack {
  /** Acknowledgement of processing that signals upstream that the
    * consumer is interested in receiving more events.
    */
  case object Continue extends Ack { self =>
    final val AsSuccess = Success(Continue)
    final val value = Some(AsSuccess)
    final val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Continue
  }

  /** Acknowledgement of processing that signals to upstream that the
    * consumer is no longer interested in receiving events.
    */
  case object Stop extends Ack { self =>
    final val AsSuccess = Success(Stop)
    final val value = Some(AsSuccess)
    final val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Stop
  }

  /** Helpers for dealing with synchronous `Future[Ack]` results,
    * powered by macros.
    */
  implicit class AckExtensions[Self <: Future[Ack]](val source: Self) extends AnyVal {
    /** Returns `true` if self is a direct reference to
      * `Continue` or `Stop`, `false` otherwise.
      */
    def isSynchronous: Boolean =
      macro Macros.isSynchronous[Self]

    /** Executes the given `callback` on `Continue`.
      *
      * Execution will happen synchronously if the `source` value is
      * a direct reference to `Continue` or `Stop`, or asynchronously
      * otherwise.
      */
    def syncOnContinue(callback: => Unit)(implicit s: Scheduler): Self =
      macro Macros.syncOnContinue[Self]

    /** Executes the given `callback` on `Stop` or on `Failure(ex)`.
      *
      * Execution will happen synchronously if the `source` value is
      * a direct reference to `Continue` or `Stop`, or asynchronously
      * otherwise.
      */
    def syncOnStopOrFailure(callback: Option[Throwable] => Unit)(implicit s: Scheduler): Self =
      macro Macros.syncOnStopOrFailure[Self]

    /** Given a mapping function, returns a new future reference that
      * is the result of a `map` operation applied to the source.
      *
      * Execution will happen synchronously if the `source` value is
      * a direct reference to `Continue` or `Stop`, or asynchronously
      * otherwise.
      */
    def syncMap(f: Ack => Ack)(implicit s: Scheduler): Future[Ack] =
      macro Macros.syncMap[Self]

    /** Given a mapping function, returns a new future reference that
      * is the result of a `flatMap` operation applied to the source.
      *
      * Execution will happen synchronously if the `source` value is
      * a direct reference to `Continue` or `Stop`, or asynchronously
      * otherwise.
      */
    def syncFlatMap(f: Ack => Future[Ack])(implicit s: Scheduler): Future[Ack] =
      macro Macros.syncFlatMap[Self]

    /** When the source future is completed, either through an exception,
      * or a value, apply the provided function.
      *
      * Execution will happen synchronously if the `source` value is
      * a direct reference to `Continue` or `Stop`, or asynchronously
      * otherwise.
      */
    def syncOnComplete(f: Try[Ack] => Unit)(implicit s: Scheduler): Unit =
      macro Macros.syncOnComplete[Self]

    /** If the source completes with a `Stop`, then complete the given
      * promise with a value.
      */
    def syncOnContinueFollow[A](p: Promise[A], value: A)(implicit s: Scheduler): Self = {
      if (source eq Continue)
        p.trySuccess(value)
      else if (source ne Stop)
        source.onComplete { r =>
          if (r.isSuccess && (r.get eq Continue))
            p.trySuccess(value)
        }

      source
    }

    /** If the source completes with a `Stop`, then complete the given
      * promise with a value.
      */
    def syncOnStopFollow[A](p: Promise[A], value: A)(implicit s: Scheduler): Self = {
      if (source eq Stop)
        p.trySuccess(value)
      else if (source ne Continue)
        source.onComplete { r =>
          if (r.isSuccess && (r.get eq Stop))
            p.trySuccess(value)
        }
      source
    }

    /** Tries converting an already completed `Future[Ack]` into a direct
      * reference to `Continue` or `Stop`. Useful for collapsing async
      * pipelines.
      */
    def syncTryFlatten(implicit r: UncaughtExceptionReporter): Future[Ack] =
      if (source == Continue || source == Stop) source else {
        if (source.isCompleted)
          source.value.get match {
            case Success(ack) => ack
            case Failure(ex) =>
              r.reportFailure(ex)
              Stop
          }
        else
          source
      }
  }
}
