/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package object misc {
  @deprecated("Switch to monix.execution.AsyncVar", "3.0.0")
  type AsyncVar[A] = monix.execution.AsyncVar[A]

  @deprecated("Switch to monix.execution.AsyncVar", "3.0.0")
  def AsyncVar = monix.execution.AsyncVar

  @deprecated("Switch to monix.execution.AsyncSemaphore", "3.0.0")
  type AsyncSemaphore = monix.execution.AsyncSemaphore

  @deprecated("Switch to monix.execution.AsyncSemaphore", "3.0.0")
  def AsyncSemaphore = monix.execution.AsyncSemaphore

  @deprecated("Switch to monix.execution.AsyncQueue", "3.0.0")
  type AsyncQueue[A] = monix.execution.AsyncQueue[A]

  @deprecated("Switch to monix.execution.AsyncQueue", "3.0.0")
  def AsyncQueue = monix.execution.AsyncQueue
}
