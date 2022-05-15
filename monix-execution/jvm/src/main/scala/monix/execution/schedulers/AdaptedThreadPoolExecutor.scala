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

package monix.execution.schedulers

import java.util.concurrent._

/** A mixin for adapting for the Java `ThreadPoolExecutor` implementation
  * to report errors using the default thread exception handler.
  */
private[schedulers] abstract class AdaptedThreadPoolExecutor(corePoolSize: Int, factory: ThreadFactory)
  extends ScheduledThreadPoolExecutor(corePoolSize, factory) {
  def reportFailure(t: Throwable): Unit

  override def afterExecute(r: Runnable, t: Throwable): Unit = {
    super.afterExecute(r, t)
    var exception: Throwable = t

    if ((exception eq null) && r.isInstanceOf[Future[_]]) {
      try {
        val future = r.asInstanceOf[Future[_]]
        if (future.isDone) future.get()
      } catch {
        case ex: ExecutionException =>
          exception = ex.getCause
        case _: InterruptedException =>
          // ignore/reset
          Thread.currentThread().interrupt()
        case _: CancellationException =>
          () // ignore
      }
    }

    if (exception ne null) reportFailure(exception)
  }
}
