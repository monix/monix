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

package monifu.reactive.internals

import scala.concurrent.Promise
import monifu.concurrent.atomic.padded.Atomic

/**
 * Represents a Promise that completes with `value` after
 * receiving a `countdownUntil` number of `countdown()` calls.
 */
private[reactive] final class PromiseCounter[T] private
  (value: T, length: Int) {
  require(length > 0, "length must be strictly positive")

  private[this] val promise = Promise[T]()
  private[this] val counter = Atomic(length)

  def future = {
    promise.future
  }

  def acquire(): Unit = {
    counter.increment()
  }

  def countdown(): Unit = {
    val current = counter.get
    val update = current - 1

    if (current > 0) {
      if (!counter.compareAndSet(current, update))
        countdown()
      else if (update == 0)
        promise.success(value)
    }
  }

  def success(value: T) = {
    promise.success(value)
  }
}

private[reactive] object PromiseCounter {
  def apply[T](value: T, countDown: Int): PromiseCounter[T] =
    new PromiseCounter[T](value, countDown)
}