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

package monix.eval.internal

import monix.eval.Task
import monix.execution.misc.NonFatal
import monix.execution.{Cancelable, Scheduler}

import scala.concurrent.Future

private[monix] object TaskFromFuture {
  /** Implementation for `Task.fromFuture`. */
  def strict[A](f: Future[A]): Task[A] = {
    if (f.isCompleted) {
      // An already computed result is synchronous
      Task.fromTry(f.value.get)
    }
    else f match {
      // Do we have a CancelableFuture?
      case c: Cancelable =>
        // Cancelable future, needs canceling
        Task.unsafeCreate { (context, cb) =>
          implicit val s = context.scheduler
          // Already completed future?
          if (f.isCompleted)
            cb.asyncApply(f.value.get)
          else {
            val conn = context.connection
            conn.push(c)
            f.onComplete { result =>
              // Async boundary should trigger frame reset
              context.frameRef.reset()
              conn.pop()
              cb(result)
            }
          }
        }
      case _ =>
        // Simple future, convert directly
        Task.unsafeCreate { (context, cb) =>
          implicit val s = context.scheduler
          if (f.isCompleted)
            cb.asyncApply(f.value.get)
          else
            f.onComplete { result =>
              // Async boundary should trigger frame reset
              context.frameRef.reset()
              cb(result)
            }
        }
    }
  }

  def deferAction[A](f: Scheduler => Future[A]): Task[A] =
    Task.unsafeCreate[A] { (context, callback) =>
      implicit val s = context.scheduler
      // Prevents violations of the Callback contract
      var streamErrors = true
      try {
        val future = f(s)
        streamErrors = false

        future.value match {
          case Some(value) =>
            // Already completed future, streaming value immediately,
            // but with light async boundary to prevent stack overflows
            callback.asyncApply(value)

          case None =>
            future match {
              case c: Cancelable =>
                // Given a cancelable future, we should use it
                val conn = context.connection
                conn.push(c)
                future.onComplete { result =>
                  // Async boundary should trigger frame reset
                  context.frameRef.reset()
                  conn.pop()
                  callback(result)
                }
              case _ =>
                // Normal future reference
                future.onComplete { result =>
                  // Async boundary should trigger frame reset
                  context.frameRef.reset()
                  callback(result)
                }
            }
        }
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) callback.asyncOnError(ex)
          else s.reportFailure(ex)
      }
    }
}
