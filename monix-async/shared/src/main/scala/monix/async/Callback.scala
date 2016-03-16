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

package monix.async

import monix.execution.UncaughtExceptionReporter

import scala.util.control.NonFatal

/** Represents a callback that should be called asynchronously
  * with the result of a computation. Used by [[monix.async.Task Task]] to signal
  * the completion of asynchronous computations on `runAsync`.
  *
  * The `onSuccess` method should be called only once, with the successful
  * result, whereas `onError` should be called if the result is an error.
  */
abstract class Callback[-T] {
  def onSuccess(value: T): Unit
  def onError(ex: Throwable): Unit
}

object Callback {
  /** Wraps any [[Callback]] into a safer implementation that
    * protects against grammar violations (e.g. `onSuccess` or `onError`
    * must be called at most once). For usage in `runAsync`.
    */
  def safe[T](cb: Callback[T])
    (implicit r: UncaughtExceptionReporter): Callback[T] =
    cb match {
      case _: SafeCallback[_] => cb
      case _ => new SafeCallback[T](cb)
    }

  /** A `SafeCallback` is a callback that ensures it can only be called
    * once, with a simple check.
    */
  private class SafeCallback[-T](underlying: Callback[T])(implicit r: UncaughtExceptionReporter)
    extends Callback[T] {

    private[this] var isActive = true

    /** To be called only once, on successful completion of a [[monix.async.Task Task]] */
    def onSuccess(value: T): Unit =
      if (isActive) {
        isActive = false
        try underlying.onSuccess(value) catch {
          case NonFatal(ex) =>
            r.reportFailure(ex)
        }
      }

    /** To be called only once, on failure of a [[monix.async.Task Task]] */
    def onError(ex: Throwable): Unit =
      if (isActive) {
        isActive = false
        try underlying.onError(ex) catch {
          case NonFatal(err) =>
            r.reportFailure(ex)
            r.reportFailure(err)
        }
      }
  }
}