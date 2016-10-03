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
  private[this] var localTasks: List[Runnable] = null
  protected def executeAsync(r: Runnable): Unit

  override final def execute(runnable: Runnable): Unit =
    runnable match {
      case _: LocalRunnable =>
        localTasks match {
          case null =>
            // If we aren't in local mode yet, start local loop
            localTasks = Nil
            localRunLoop(runnable, Nil)
          case some =>
            // If we are already in batching mode, add to stack
            localTasks = runnable :: some
        }
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }

  @tailrec private def localRunLoop(head: Runnable, tail: List[Runnable]): Unit = {
    try {
      head.run()
    } catch {
      case ex: Throwable =>
        // Sending everything to the underlying context,
        // so that we can throw
        val remaining = tail ::: localTasks
        localTasks = null
        forkTheRest(remaining)
        if (NonFatal(ex)) reportFailure(ex) else throw ex
    }

    tail match {
      case h2 :: t2 => localRunLoop(h2, t2)
      case Nil =>
        localTasks match {
          case null => ()
          case Nil =>
            localTasks = null
          case h2 :: t2 =>
            localTasks = Nil
            localRunLoop(h2, t2)
        }
    }
  }

  private def forkTheRest(rest: List[Runnable]): Unit = {
    final class ResumeRun(head: Runnable, tail: List[Runnable]) extends Runnable {
      def run(): Unit = {
        localTasks = Nil
        localRunLoop(head,tail)
      }
    }

    rest match {
      case null | Nil => ()
      case head :: tail => executeAsync(new ResumeRun(head, tail))
    }
  }
}
