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

package monix.streams.internal.concurrent

import scala.concurrent.{Future, Promise}

private[monix] final class PromiseSuccessRunnable[T] private
  (p: Promise[T], value: T) extends Runnable {

  def run(): Unit = {
    p.trySuccess(value)
  }
}

private[monix] object PromiseSuccessRunnable {
  def apply[T](p: Promise[T], value: T): Runnable =
    new PromiseSuccessRunnable[T](p, value)
}

private[monix] final class PromiseFailureRunnable[T] private
  (p: Promise[T], ex: Throwable) extends Runnable {

  def run(): Unit = {
    p.tryFailure(ex)
  }
}

private[monix] object PromiseFailureRunnable {
  def apply[T](p: Promise[T], ex: Throwable): Runnable =
    new PromiseFailureRunnable[T](p, ex)
}

private[monix] final class PromiseCompleteWithRunnable[T] private
  (p: Promise[T], f: Future[T]) extends Runnable {

  def run(): Unit = {
    p.tryCompleteWith(f)
  }
}

private[monix] object PromiseCompleteWithRunnable {
  def apply[T](p: Promise[T], f: Future[T]): Runnable =
    new PromiseCompleteWithRunnable[T](p, f)
}