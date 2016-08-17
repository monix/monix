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

package monix.execution.schedulers

import monix.execution.Scheduler
import scala.annotation.tailrec

/** Adds trampoline execution capabilities to
  * [[monix.execution.Scheduler schedulers]], when
  * inherited.
  *
  * When it receives [[LocalRunnable]] instances, it
  * switches to a trampolined mode where all incoming
  * [[LocalRunnable LocalRunnables]] are executed on the
  * current thread.
  *
  * This is useful for light-weight callbacks. The idea is
  * borrowed from the implementation of
  * `scala.concurrent.Future`, except that in this case we
  * don't care about a blocking context, the implementation
  * being more light-weight.
  *
  * Currently used as an optimization by `Task` in processing
  * its internal callbacks.
  */
trait LocalBatchingExecutor extends Scheduler {
  private[this] val localTasks = new ThreadLocal[List[Runnable]]()

  protected def executeAsync(r: Runnable): Unit

  override final def execute(runnable: Runnable): Unit =
    runnable match {
      case _: LocalRunnable =>
        localTasks.get match {
          case null =>
            // If we aren't in local mode yet, start local loop
            localTasks.set(Nil)
            localRunLoop(runnable)
          case some =>
            // If we are already in batching mode, add to stack
            localTasks.set(runnable :: some)
        }
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }

  @tailrec private def localRunLoop(current: Runnable): Unit = {
    try {
      current.run()
    } catch {
      case ex: Throwable =>
        // Sending everything to the underlying context,
        // so that we can throw
        localTasks.get().foreach(executeAsync)
        localTasks.set(null)
        throw ex
    }

    localTasks.get() match {
      case null => ()
      case Nil => localTasks.set(null)
      case other :: tail =>
        localTasks.set(tail)
        localRunLoop(other)
    }
  }
}
