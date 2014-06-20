/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.concurrent.async

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.extensions.FutureExtensions
import monifu.concurrent.Scheduler

final class AsyncSemaphore private (limit: Int) {
  private[this] val queue = {
    val elems = for (i <- 0 until limit) yield ()
    AsyncQueue(elems :_*)
  }

  def acquire[T](timeout: FiniteDuration)(f: => Future[T])(implicit s: Scheduler): Future[T] =
    queue.poll().flatMap { _ =>
      val t = f.withTimeout(timeout)
      t.onComplete { _ => queue.offer(()) }
      t
    }

  def acquire[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    queue.poll().flatMap { _ =>
      f.onComplete { _ => queue.offer(()) }
      f
    }
}

object AsyncSemaphore {
  def apply(limit: Int): AsyncSemaphore =
    new AsyncSemaphore(limit)
}
