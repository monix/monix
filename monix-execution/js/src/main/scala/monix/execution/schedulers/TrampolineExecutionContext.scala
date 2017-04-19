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


import monix.execution.misc.NonFatal

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext

/** A `scala.concurrentExecutionContext` implementation
  * that executes runnables immediately, on the current thread,
  * by means of a trampoline implementation.
  *
  * Can be used in some cases to keep the asynchronous execution
  * on the current thread, as an optimization, but be warned,
  * you have to know what you're doing.
  *
  * The `TrampolineExecutionContext` keeps a reference to another
  * `underlying` context, to which it defers for:
  *
  *  - reporting errors
  *  - deferring the rest of the queue in problematic situations
  *
  * Deferring the rest of the queue happens:
  *
  *  - in case we have a runnable throwing an exception, the rest
  *    of the tasks get re-scheduled for execution by using
  *    the `underlying` context
  *  - in case we have a runnable triggering a Scala `blocking`
  *    context, the rest of the tasks get re-scheduled for execution
  *    on the `underlying` context to prevent any deadlocks
  *
  * Thus this implementation is compatible with the
  * `scala.concurrent.BlockContext`, detecting `blocking` blocks and
  * reacting by forking the rest of the queue to prevent deadlocks.
  *
  * @param underlying is the `ExecutionContext` to which the it defers
  *        to in case real asynchronous is needed
  */
final class TrampolineExecutionContext private (underlying: ExecutionContext)
  extends ExecutionContext {

  private[this] var immediateQueue = mutable.Queue.empty[Runnable]
  private[this] var withinLoop = false

  override def execute(runnable: Runnable): Unit = {
    if (!withinLoop) {
      withinLoop = true
      try immediateLoop(runnable) finally {
        withinLoop = false
      }
    } else {
      immediateQueue.enqueue(runnable)
    }
  }

  private def forkTheRest(): Unit = {
    final class ResumeRun(head: Runnable, rest: mutable.Queue[Runnable])
      extends Runnable {

      def run(): Unit = {
        if (rest.nonEmpty) immediateQueue.enqueue(rest:_*)
        immediateLoop(head)
      }
    }

    if (immediateQueue.nonEmpty) {
      val rest = immediateQueue
      immediateQueue = mutable.Queue.empty[Runnable]
      val head = rest.dequeue()
      underlying.execute(new ResumeRun(head, rest))
    }
  }

  @tailrec
  private def immediateLoop(task: Runnable): Unit = {
    try {
      task.run()
    } catch {
      case ex: Throwable =>
        forkTheRest()
        if (NonFatal(ex)) reportFailure(ex)
        else throw ex
    }

    if (immediateQueue.nonEmpty) {
      val next = immediateQueue.dequeue()
      immediateLoop(next)
    }
  }

  override def reportFailure(t: Throwable): Unit =
    underlying.reportFailure(t)
}

object TrampolineExecutionContext {
  /** Builds a [[TrampolineExecutionContext]] instance.
    *
    * @param underlying is the `ExecutionContext` to which the
    *        it defers to in case asynchronous or time-delayed execution
    *        is needed
    */
  def apply(underlying: ExecutionContext): TrampolineExecutionContext =
    new TrampolineExecutionContext(underlying)
}