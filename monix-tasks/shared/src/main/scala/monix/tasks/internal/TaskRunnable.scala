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
 *
 */

package monix.tasks.internal

import monix.tasks.Task
import scala.util.control.NonFatal

/** Helpers for building Task-related Runnable instances.
  *
  * For now this is starting to be preferred versus building
  * anonymous classes, because of code reuse and control over
  * what gets captured.
  */
private[tasks] object TaskRunnable {
  /**
    * Call callback.onSuccess(value)
    * Resets the stackDepth to 1, as the call will be async
    */
  final class AsyncOnSuccess[T] private (cb: Task.UnsafeCallback[T], value: T)
    extends Runnable {

    def run(): Unit =
      try cb.onSuccess(value, stackDepth = 1) catch {
        case NonFatal(ex) =>
          cb.onError(ex, stackDepth = 1)
      }
  }

  object AsyncOnSuccess {
    /** Builder for [[AsyncOnSuccess]] */
    def apply[T](cb: Task.UnsafeCallback[T], value: T): Runnable =
      new AsyncOnSuccess[T](cb, value)
  }

  /**
    * Call callback.onSuccess(value)
    * Resets the stackDepth to 1, as the call will be async
    */
  final class AsyncOnError[T] private (cb: Task.UnsafeCallback[T], ex: Throwable)
    extends Runnable {

    def run(): Unit = {
      cb.onError(ex, stackDepth = 1)
    }
  }

  object AsyncOnError {
    /** Builder for [[AsyncOnError]] */
    def apply[T](cb: Task.UnsafeCallback[T], ex: Throwable): Runnable =
      new AsyncOnError[T](cb, ex)
  }
}
