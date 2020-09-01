/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import scala.util.control.NonFatal
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import scala.concurrent.duration.Duration
import scala.concurrent.{CanAwait, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/** Represents an acknowledgement of processing that a consumer
  * sends back upstream. Useful to implement back-pressure.
  */
sealed abstract class Ack extends Future[Ack] with Serializable {
  val AsSuccess: Success[Ack]

  // For Scala 2.12 compatibility
  final def transform[S](f: (Try[Ack]) => Try[S])(implicit executor: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    onComplete(r =>
      p.complete(
        try f(r)
        catch { case t if NonFatal(t) => Failure(t) }))
    p.future
  }

  // For Scala 2.12 compatibility
  final def transformWith[S](f: (Try[Ack]) => Future[S])(implicit executor: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    onComplete(r =>
      p.completeWith(
        try f(r)
        catch { case t if NonFatal(t) => Future.failed(t) }))
    p.future
  }

  final def onComplete[U](func: Try[Ack] => U)(implicit executor: ExecutionContext): Unit =
    executor.execute(new Runnable {
      def run(): Unit = { func(AsSuccess); () }
    })
}

object Ack {
  /** Acknowledgement of processing that signals upstream that the
    * consumer is interested in receiving more events.
    */
  case object Continue extends Ack { self =>
    val AsSuccess = Success(Continue)
    val value = Some(AsSuccess)
    val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Continue
  }

  /** Acknowledgement of processing that signals to upstream that the
    * consumer is no longer interested in receiving events.
    */
  case object Stop extends Ack { self =>
    val AsSuccess = Success(Stop)
    val value = Some(AsSuccess)
    val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Stop
  }

  /** Helpers for dealing with synchronous `Future[Ack]` results,
    * powered by macros.
    *
    * @define syncModel Execution will happen without any hard
    *         asynchronous boundaries — in case the `source`
    *         is an `Ack` value (e.g. `Continue` or `Stop`) then
    *         execution will be immediate, otherwise execution
    *         will be trampolined (being execute on Monix's
    *         `TrampolineExecutionContext`).
    *
    *         WARN: in case the source is an `Ack` value
    *         (e.g. `Continue` or `Stop`) and the execution being
    *         immediate, with no async boundaries, this means that
    *         application of this function is *stack unsafe*!
    *
    *         Use with great care as an optimization. Don't use
    *         it in tail-recursive loops!
    */
  implicit class AckExtensions[Self <: Future[Ack]](val source: Self) extends AnyVal {
    /** Returns `true` if self is a direct reference to
      * `Continue` or `Stop`, `false` otherwise.
      */
    def isSynchronous: Boolean =
      (source eq Continue) || (source eq Stop)

    /** Executes the given `callback` on `Continue`.
      *
      * $syncModel
      *
      * @param r is an exception reporter used for reporting errors
      *        triggered by `thunk`
      */
    def syncOnContinue(thunk: => Unit)(implicit r: UncaughtExceptionReporter): Self = {
      if (source eq Continue)
        try thunk
        catch {
          case e if NonFatal(e) => r.reportFailure(e)
        }
      else if (source ne Stop)
        source.onComplete {
          case Success(Continue) =>
            try thunk
            catch { case e if NonFatal(e) => r.reportFailure(e) }
          case _ =>
            ()
        }(immediate)
      source
    }

    /** Executes the given `callback` on `Stop` or on `Failure(ex)`.
      *
      * $syncModel
      *
      * @param r is an exception reporter used for reporting errors
      *        triggered by `cb`
      */
    def syncOnStopOrFailure(cb: Option[Throwable] => Unit)(implicit r: UncaughtExceptionReporter): Self = {
      if (source eq Stop)
        try cb(None)
        catch {
          case e if NonFatal(e) => r.reportFailure(e)
        }
      else if (source ne Continue)
        source.onComplete { ack =>
          try ack match {
            case Success(Stop) => cb(None)
            case Failure(e) => cb(Some(e))
            case _ => ()
          } catch {
            case e if NonFatal(e) => r.reportFailure(e)
          }
        }(immediate)
      source
    }

    /** Given a mapping function, returns a new future reference that
      * is the result of a `map` operation applied to the source.
      *
      * $syncModel
      *
      * @param f is the mapping function used to transform the source
      * @param r is an exception reporter used for reporting errors
      *        triggered by `f`
      */
    def syncMap(f: Ack => Ack)(implicit r: UncaughtExceptionReporter): Future[Ack] =
      syncFlatMap(f)(r)

    /** Given a mapping function, returns a new future reference that
      * is the result of a `flatMap` operation applied to the source.
      *
      * $syncModel
      *
      * @param f is the mapping function used to transform the source
      * @param r is an exception reporter used for reporting errors
      *        triggered by `f`
      */
    def syncFlatMap(f: Ack => Future[Ack])(implicit r: UncaughtExceptionReporter): Future[Ack] = {
      if ((source eq Continue) || (source eq Stop))
        try f(source.asInstanceOf[Ack])
        catch {
          case e if NonFatal(e) =>
            r.reportFailure(e)
            Stop
        }
      else
        source.flatMap(_.syncFlatMap(f)(r))(immediate)
    }

    /** When the source future is completed, either through an exception,
      * or a value, apply the provided function.
      *
      * $syncModel
      *
      * @param r is a reporter for exceptions thrown by `f`
      */
    def syncOnComplete(f: Try[Ack] => Unit)(implicit r: UncaughtExceptionReporter): Unit = {
      if ((source eq Continue) || (source eq Stop))
        try f(Success(source.asInstanceOf[Ack]))
        catch {
          case e if NonFatal(e) => r.reportFailure(e)
        }
      else
        source.onComplete { ack =>
          try f(ack)
          catch { case e if NonFatal(e) => r.reportFailure(e) }
        }(immediate)
    }

    /** If the source completes with a `Stop`, then complete the given
      * promise with a value.
      */
    def syncOnContinueFollow[A](p: Promise[A], value: A): Self = {
      if (source eq Continue)
        p.trySuccess(value)
      else if (source ne Stop)
        source.onComplete { r =>
          if (r.isSuccess && (r.get eq Continue))
            p.trySuccess(value)
        }(immediate)
      source
    }

    /** If the source completes with a `Stop`, then complete the given
      * promise with a value.
      */
    def syncOnStopFollow[A](p: Promise[A], value: A): Self = {
      if (source eq Stop)
        p.trySuccess(value)
      else if (source ne Continue)
        source.onComplete { r =>
          if (r.isSuccess && (r.get eq Stop))
            p.trySuccess(value)
        }(immediate)
      source
    }

    /** Tries converting an already completed `Future[Ack]` into a direct
      * reference to `Continue` or `Stop`. Useful for collapsing async
      * pipelines.
      */
    def syncTryFlatten(implicit r: UncaughtExceptionReporter): Future[Ack] =
      if (source == Continue || source == Stop) source
      else {
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
