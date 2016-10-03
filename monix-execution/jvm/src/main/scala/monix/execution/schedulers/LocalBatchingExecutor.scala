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
import scala.concurrent.{BlockContext, CanAwait}
import scala.util.control.NonFatal

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
  * `scala.concurrent.Future`. Currently used as an
  * optimization by `Task` in processing its internal callbacks.
  */
trait LocalBatchingExecutor extends Scheduler {
  private[this] val localContext = LocalBatchingExecutor.localContext
  private[this] val localTasks = new ThreadLocal[List[Runnable]]()

  protected def executeAsync(r: Runnable): Unit

  override final def execute(runnable: Runnable): Unit =
    runnable match {
      case _: LocalRunnable =>
        localTasks.get match {
          case null =>
            // If we aren't in local mode yet, start local loop
            localTasks.set(Nil)
            val parentContext = localContext.get()
            try {
              localContext.set(localBlockContext)
              localRunLoop(runnable)
            } finally {
              localContext.set(parentContext)
            }
          case some =>
            // If we are already in batching mode, add to stack
            localTasks.set(runnable :: some)
        }
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }

  private[this] val localBlockContext: BlockContext =
    new BlockContext {
      def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
        // In case of blocking, execute all scheduled local tasks on
        // a separate thread, otherwise we could end up with a dead-lock
        forkTheRest(Nil)
        thunk
      }
    }

  @tailrec private def localRunLoop(head: Runnable): Unit = {
    try {
      head.run()
    } catch {
      case ex: Throwable =>
        // Sending everything else to the underlying context
        forkTheRest(null)
        if (NonFatal(ex)) reportFailure(ex) else throw ex
    }

    localTasks.get() match {
      case null => ()
      case Nil =>
        localTasks.set(null)
      case h2 :: t2 =>
        localTasks.set(t2)
        localRunLoop(h2)
    }
  }

  private def forkTheRest(newLocalTasks: Nil.type): Unit = {
    final class ResumeRun(head: Runnable, rest: List[Runnable]) extends Runnable {
      def run(): Unit = {
        localTasks.set(rest)
        localRunLoop(head)
      }
    }

    val rest = localTasks.get()
    localTasks.set(newLocalTasks)

    rest match {
      case null | Nil => ()
      case head :: tail =>
        executeAsync(new ResumeRun(head, tail))
    }
  }
}

object LocalBatchingExecutor {
  /** Returns the `localContext`, allowing us to bypass calling
    * `BlockContext.withBlockContext`, as an optimization trick.
    */
  private val localContext: ThreadLocal[BlockContext] = {
    val method = BlockContext.getClass.getDeclaredMethods
      .find(_.getName.endsWith("contextLocal"))
      .getOrElse(throw new NoSuchMethodError("BlockContext.contextLocal"))

    method.setAccessible(true)
    method.invoke(BlockContext).asInstanceOf[ThreadLocal[BlockContext]]
  }
}
