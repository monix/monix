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
import monix.execution.Cancelable
import scala.concurrent.Future

private[monix] object TaskFromFuture {
  /**
    * Implementation for `Task.fromFuture`
    */
  def apply[A](f: Future[A]): Task[A] = {
    if (f.isCompleted) {
      // An already computed result is synchronous
      Task.fromTry(f.value.get)
    }
    else f match {
      // Do we have a CancelableFuture?
      case c: Cancelable =>
        // Cancelable future, needs canceling
        Task.unsafeCreate { (context, cb) =>
          import context.implicitScheduler
          // Already completed future?
          if (f.isCompleted) cb.asyncApply(f.value.get) else {
            val conn = context.connection
            conn.push(c)
            f.onComplete { result =>
              conn.pop()
              cb(result)
            }
          }
        }
      case _ =>
        // Simple future, convert directly
        Task.unsafeCreate { (context, cb) =>
          import context.implicitScheduler
          if (f.isCompleted)
            cb.asyncApply(f.value.get)
          else
            f.onComplete(cb)
        }
    }
  }
}
