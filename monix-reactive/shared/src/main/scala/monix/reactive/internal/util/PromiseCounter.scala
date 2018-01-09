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

package monix.reactive.internal.util

import monix.execution.atomic.Atomic
import scala.concurrent.{Future, Promise}

/**
  * Represents a Promise that completes with `value` after
  * receiving a `countdownUntil` number of `countdown()` calls.
  */
private[monix] final class PromiseCounter[A] private (value: A, initial: Int) {
  require(initial > 0, "length must be strictly positive")

  private[this] val promise = Promise[A]()
  private[this] val counter = Atomic(initial)

  def future: Future[A] =
    promise.future

  def acquire(): Unit =
    counter.increment()

  def countdown(): Unit = {
    val update = counter.decrementAndGet()
    if (update == 0) promise.success(value)
  }

  def success(value: A): Unit =
    promise.success(value)
}

private[monix] object PromiseCounter {
  def apply[A](value: A, initial: Int): PromiseCounter[A] =
    new PromiseCounter[A](value, initial)
}