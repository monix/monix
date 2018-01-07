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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.misc.NonFatal
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import scala.concurrent.Future

private[eval] object TaskFromFuture {
  /** Implementation for `Task.fromFuture`. */
  def strict[A](f: Future[A]): Task[A] = {
    if (f.isCompleted) {
      // An already computed result is synchronous
      Task.fromTry(f.value.get)
    } else f match {
      // Do we have a CancelableFuture?
      case cf: CancelableFuture[A] @unchecked =>
        // Cancelable future, needs canceling
        Task.unsafeCreate(startCancelable(_, _, cf, cf.cancelable))
      case _ =>
        // Simple future, convert directly
        Task.unsafeCreate(startSimple(_, _, f))
    }
  }

  def deferAction[A](f: Scheduler => Future[A]): Task[A] =
    Task.unsafeCreate[A] { (context, callback) =>
      implicit val sc = context.scheduler
      // Prevents violations of the Callback contract
      var streamErrors = true
      try {
        val future = f(sc)
        streamErrors = false

        future.value match {
          case Some(value) =>
            // Already completed future, streaming value immediately,
            // but with light async boundary to prevent stack overflows
            callback.asyncApply(value)
          case None =>
            future match {
              case cf: CancelableFuture[A] @unchecked =>
                startCancelable(context, callback, cf, cf.cancelable)
              case _ =>
                startSimple(context, callback, future)
            }
        }
      } catch {
        case ex if NonFatal(ex) =>
          if (streamErrors) callback.asyncOnError(ex)
          else sc.reportFailure(ex)
      }
    }

  def build[A](f: Future[A], c: Cancelable): Task[A] =
    Task.unsafeCreate[A](startCancelable(_, _, f, c))

  private def startSimple[A](ctx: Task.Context, cb: Callback[A], f: Future[A]) = {
    implicit val sc = ctx.scheduler
    f.value match {
      case Some(value) =>
        // Short-circuit the processing, as future is already complete
        cb.apply(value)

      case None =>
        f.onComplete { result =>
          // Async boundary should trigger frame reset
          ctx.frameRef.reset()
          cb(result)
        }
    }
  }

  private def startCancelable[A](ctx: Task.Context, cb: Callback[A], f: Future[A], c: Cancelable): Unit = {
    implicit val sc = ctx.scheduler
    f.value match {
      case Some(value) =>
        // Short-circuit the processing, as future is already complete
        cb.apply(value)

      case None =>
        // Given a cancelable future, we should use it
        val conn = ctx.connection
        conn.push(c)
        // Async boundary
        f.onComplete { result =>
          // Async boundary should trigger frame reset
          ctx.frameRef.reset()
          conn.pop()
          cb(result)
        }
    }
  }
}
