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

package monix.eval.internal

import monix.eval.Task.Context
import monix.execution._
import monix.eval.Task
import monix.execution.cancelables.SingleAssignCancelable
import scala.util.control.NonFatal
import monix.execution.schedulers.TrampolinedRunnable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[eval] object TaskFromFuture {
  /** Implementation for `Task.fromFuture`. */
  def strict[A](f: Future[A]): Task[A] = {
    f.value match {
      case None =>
        f match {
          // Do we have a CancelableFuture?
          case cf: CancelableFuture[A] @unchecked =>
            // Cancelable future, needs canceling
            rawAsync(startCancelable(_, _, cf, cf.cancelable))
          case _ =>
            // Simple future, convert directly
            rawAsync(startSimple(_, _, f))
        }
      case Some(value) =>
        Task.fromTry(value)
    }
  }

  /** Implementation for `Task.deferFutureAction`. */
  def deferAction[A](f: Scheduler => Future[A]): Task[A] =
    rawAsync[A] { (ctx, cb) =>
      implicit val sc = ctx.scheduler
      // Prevents violations of the Callback contract
      var streamErrors = true
      try {
        val future = f(sc)
        streamErrors = false

        future.value match {
          case Some(value) =>
            cb(value)
          case None =>
            future match {
              case cf: CancelableFuture[A] @unchecked =>
                startCancelable(ctx, cb, cf, cf.cancelable)
              case _ =>
                startSimple(ctx, cb, future)
            }
        }
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors) cb.onError(ex)
          else sc.reportFailure(ex)
      }
    }

  def fromCancelablePromise[A](p: CancelablePromise[A]): Task[A] = {
    val start: Start[A] = (ctx, cb) => {
      implicit val ec = ctx.scheduler
      if (p.isCompleted) {
        p.subscribe(trampolinedCB(cb, null))
        ()
      } else {
        val conn = ctx.connection
        val ref = SingleAssignCancelable()
        conn.push(ref)
        ref := p.subscribe(trampolinedCB(cb, conn))
        ()
      }
    }

    Task.Async(
      start,
      trampolineBefore = false,
      trampolineAfter = false,
      restoreLocals = true
    )
  }

  private def rawAsync[A](start: (Context, Callback[Throwable, A]) => Unit): Task[A] =
    Task.Async(
      start,
      trampolineBefore = true,
      trampolineAfter = false,
      restoreLocals = true
    )

  private def startSimple[A](ctx: Task.Context, cb: Callback[Throwable, A], f: Future[A]) = {

    f.value match {
      case Some(value) =>
        cb(value)
      case None =>
        f.onComplete { result =>
          cb(result)
        }(ctx.scheduler)
    }
  }

  private def startCancelable[A](ctx: Task.Context, cb: Callback[Throwable, A], f: Future[A], c: Cancelable): Unit = {

    f.value match {
      case Some(value) =>
        cb(value)
      case None =>
        // Given a cancelable future, we should use it
        val conn = ctx.connection
        conn.push(c)(ctx.scheduler)
        // Async boundary
        f.onComplete { result =>
          conn.pop()
          cb(result)
        }(ctx.scheduler)
    }
  }

  private def trampolinedCB[A](cb: Callback[Throwable, A], conn: TaskConnection)(implicit
    ec: ExecutionContext): Try[A] => Unit = {

    new (Try[A] => Unit) with TrampolinedRunnable {
      private[this] var value: Try[A] = _

      def apply(value: Try[A]): Unit = {
        this.value = value
        ec.execute(this)
      }

      def run(): Unit = {
        if (conn ne null) conn.pop()
        val v = value
        value = null
        cb(v)
      }
    }
  }
}
