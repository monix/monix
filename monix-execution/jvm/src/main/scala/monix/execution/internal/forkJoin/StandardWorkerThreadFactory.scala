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

package monix.execution.internal.forkJoin

import java.util.concurrent.ThreadFactory

private[monix] final class StandardWorkerThreadFactory(
  prefix: String,
  uncaught: Thread.UncaughtExceptionHandler,
  daemonic: Boolean)
  extends ThreadFactory with ForkJoinWorkerThreadFactory {

  def wire[T <: Thread](thread: T): T = {
    thread.setDaemon(daemonic)
    thread.setUncaughtExceptionHandler(uncaught)
    thread.setName(prefix + "-" + thread.getId)
    thread
  }

  override def newThread(r: Runnable): Thread =
    wire(new Thread(r))

  override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread =
    wire(new ForkJoinWorkerThread(pool) {})
}
