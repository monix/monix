/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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
final class PromiseCounter[T] private (value: T, countdownUntil: Int) {
  require(countdownUntil > 0, "countdownUntil must be strictly positive")

  private[this] val promise = Promise[T]()
  private[this] val counter = Atomic(0)

  def future = promise.future

  def countdown(): Unit =
    if (counter.incrementAndGet() >= countdownUntil) {
      promise.success(value)
    }

  def success(value: T) = {
    promise.success(value)
  }
}

object PromiseCounter {
  def apply[T](value: T, countDown: Int): PromiseCounter[T] =
    new PromiseCounter[T](value, countDown)
}