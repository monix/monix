/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

package monifu.concurrent

import scala.util.control.NonFatal

/**
 * Represents a callback that should be called asynchronously,
 * having the execution managed by the given `scheduler`.
 * Used by [[Task]] to signal the completion of asynchronous
 * computations.
 *
 * The `scheduler` represents our execution context under which
 * the asynchronous computation (leading to either `onSuccess` or `onError`)
 * should run.
 *
 * The `onSuccess` method is called only once, with the successful
 * result of our asynchronous computation, whereas `onError` is called
 * if the result is an error.
 */
trait TaskCallback[-T] {
  def trampoline: Trampoline
  def scheduler: Scheduler
  def onSuccess(value: T): Unit
  def onError(ex: Throwable): Unit
}

object TaskCallback {
  /** Extension methods for [[TaskCallback]] */
  implicit class Extensions[-T](val callback: TaskCallback[T]) extends AnyVal {
    /** Push a task in the scheduler for triggering onSuccess */
    def asyncOnSuccess(value: T): Unit =
      callback.scheduler.execute(new Runnable {
        def run(): Unit = callback.onSuccess(value)
      })

    /** Push a task in the scheduler for triggering onError */
    def asyncOnError(ex: Throwable): Unit =
      callback.scheduler.execute(new Runnable {
        def run(): Unit = callback.onError(ex)
      })
  }

  /**
   * Wraps any [[TaskCallback]] into an implementation that protects against
   * errors being triggered by onSuccess and onError and that prevents
   * multiple onSuccess and onError calls from being made.
   */
  def safe[T](callback: TaskCallback[T]): TaskCallback[T] =
    callback match {
      case ref: SafeCallback[_] => ref.asInstanceOf[SafeCallback[T]]
      case _ => new SafeCallback[T](callback)
    }

  /**
   * Implementation for a safe callback, a callback that protects against
   * errors being triggered by onSuccess and onError and that prevents
   * multiple onSuccess and onError calls from being made.
   */
  private final class SafeCallback[-T](underlying: TaskCallback[T]) extends TaskCallback[T] {
    // very simple protection for grammar
    private[this] var isDone = false
    val trampoline = underlying.trampoline
    val scheduler = underlying.scheduler

    def onSuccess(value: T): Unit =
      if (!isDone) {
        isDone = true
        try underlying.onSuccess(value) catch {
          case NonFatal(ex) => pushError(ex)
        }
      }

    def onError(ex: Throwable): Unit =
      if (!isDone) {
        isDone = true
        pushError(ex)
      }

    private[this] def pushError(ex: Throwable): Unit =
      try underlying.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(ex)
          scheduler.reportFailure(err)
      }
  }
}
