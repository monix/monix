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

package monifu.internal.concurrent

import scala.concurrent.{Future, Promise}

private[monifu] final class PromiseSuccessRunnable[T] private
  (p: Promise[T], value: T) extends Runnable {

  def run(): Unit = {
    p.trySuccess(value)
  }
}

private[monifu] object PromiseSuccessRunnable {
  def apply[T](p: Promise[T], value: T): Runnable =
    new PromiseSuccessRunnable[T](p, value)
}

private[monifu] final class PromiseFailureRunnable[T] private
  (p: Promise[T], ex: Throwable) extends Runnable {

  def run(): Unit = {
    p.tryFailure(ex)
  }
}

private[monifu] object PromiseFailureRunnable {
  def apply[T](p: Promise[T], ex: Throwable): Runnable =
    new PromiseFailureRunnable[T](p, ex)
}

private[monifu] final class PromiseCompleteWithRunnable[T] private
  (p: Promise[T], f: Future[T]) extends Runnable {

  def run(): Unit = {
    p.tryCompleteWith(f)
  }
}

private[monifu] object PromiseCompleteWithRunnable {
  def apply[T](p: Promise[T], f: Future[T]): Runnable =
    new PromiseCompleteWithRunnable[T](p, f)
}