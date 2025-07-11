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

package monix.execution.internal

import monix.execution.internal.Trampoline.ResumeRun
import monix.execution.internal.collection.ChunkedArrayQueue

import scala.annotation.tailrec
import scala.concurrent.{ BlockContext, CanAwait, ExecutionContext }
import scala.util.control.NonFatal

private[execution] class Trampoline(
  private var immediateQueue: ChunkedArrayQueue[Runnable] = Trampoline.makeQueue(),
  private val isForked: Boolean = false,
) {
  private var withinLoop: Boolean = false

  def startLoop(runnable: Runnable, ec: ExecutionContext): Unit = {
    withinLoop = true
    try immediateLoop(runnable, ec)
    finally {
      withinLoop = false
    }
  }

  final def execute(runnable: Runnable, ec: ExecutionContext): Unit = {
    if (!withinLoop) {
      startLoop(runnable, ec)
    } else {
      immediateQueue.enqueue(runnable)
    }
  }

  private def forkTheRest(ec: ExecutionContext): Unit = {
    val head = immediateQueue.dequeue()
    if (head ne null) {
      val rest = immediateQueue.shallowCopy()
      if (!isForked) {
        // forked Trampoline is of one-time use and will get discarded,
        // no need to maintain the state here
        immediateQueue = Trampoline.makeQueue()
      }
      ec.execute(new ResumeRun(head, rest, ec))
    }
  }

  @tailrec
  private final def immediateLoop(task: Runnable, ec: ExecutionContext): Unit = {
    try {
      task.run()
    } catch {
      case ex: Throwable =>
        forkTheRest(ec)
        if (NonFatal(ex)) ec.reportFailure(ex)
        else throw ex
    }

    val next = immediateQueue.dequeue()
    if (next ne null) immediateLoop(next, ec)
  }

  protected final def trampolineContext(parentContext: BlockContext, ec: ExecutionContext): BlockContext =
    new BlockContext {
      def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
        // In case of blocking, execute all scheduled local tasks on
        // a separate thread, otherwise we could end up with a dead-lock
        forkTheRest(ec)
        if (parentContext ne null) {
          parentContext.blockOn(thunk)
        } else {
          thunk
        }
      }
    }
}

object Trampoline {
  final class ResumeRun(head: Runnable, rest: ChunkedArrayQueue[Runnable], ec: ExecutionContext) extends Runnable {

    def run(): Unit = new Trampoline(rest, isForked = true).immediateLoop(head, ec)
  }

  private def makeQueue(): ChunkedArrayQueue[Runnable] = ChunkedArrayQueue[Runnable](chunkSize = 16)
}
