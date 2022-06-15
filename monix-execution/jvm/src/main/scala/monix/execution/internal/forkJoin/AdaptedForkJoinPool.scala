/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.execution.internal.forkJoin

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.{ForkJoinPool, ForkJoinTask, ForkJoinWorkerThread, TimeUnit}

private[monix] final class AdaptedForkJoinPool(
  parallelism: Int,
  maxThreads: Int,
  factory: ForkJoinWorkerThreadFactory,
  handler: UncaughtExceptionHandler,
  asyncMode: Boolean)
  extends ForkJoinPool(
    parallelism,
    factory,
    handler,
    asyncMode,
    0,
    maxThreads,
    1,
    (_: ForkJoinPool) => true,
    AdaptedForkJoinPool.DefaultKeepAliveMillis,
    TimeUnit.MILLISECONDS
  ) {

  override def execute(runnable: Runnable): Unit = {
    val fjt: ForkJoinTask[_] = runnable match {
      case t: ForkJoinTask[_] => t
      case r => new AdaptedForkJoinTask(r)
    }
    Thread.currentThread match {
      case fjw: ForkJoinWorkerThread if fjw.getPool eq this =>
        fjt.fork()
        ()
      case _ =>
        super.execute(fjt)
    }
  }
}
private[monix] object AdaptedForkJoinPool {
  // the same as ForkJoinPool.DEFAULT_KEEPALIVE (which is private)
  final val DefaultKeepAliveMillis = 60_000L
}
