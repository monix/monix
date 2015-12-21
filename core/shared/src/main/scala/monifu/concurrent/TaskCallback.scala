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
 *
 * NOTE: implementors must place an upper bound on possible synchronous
 * recursion between [[Task.unsafeRun(c* unsafeRun]] and `onSuccess` and
 * `onError` otherwise we can end up with an open synchronous recursion that
 * can lead to stack overflow errors, e.g.
 * `unsafeRun -> onSuccess -> unsafeRun -> onSuccess -> etc`.
 */
trait TaskCallback[-T] {
  def scheduler: Scheduler
  def onSuccess(value: T): Unit
  def onError(ex: Throwable): Unit
}
