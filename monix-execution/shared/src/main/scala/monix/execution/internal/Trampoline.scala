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

import monix.execution.internal.collection.ChunkedArrayQueue
import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.concurrent.{ BlockContext, CanAwait, ExecutionContext }

private[execution] class Trampoline {
  private def makeQueue(): ChunkedArrayQueue[Runnable] = ChunkedArrayQueue[Runnable](chunkSize = 16)
  private[this] var immediateQueue = makeQueue()
  private[this] var withinLoop = false

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

  protected final def forkTheRest(ec: ExecutionContext): Unit = {
    final class ResumeRun(head: Runnable, rest: ChunkedArrayQueue[Runnable]) extends Runnable {

      def run(): Unit = {
        immediateQueue.enqueueAll(rest)
        immediateLoop(head, ec)
      }
    }

    val head = immediateQueue.dequeue()
    if (head ne null) {
      val rest = immediateQueue
      immediateQueue = makeQueue()
      ec.execute(new ResumeRun(head, rest))
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
