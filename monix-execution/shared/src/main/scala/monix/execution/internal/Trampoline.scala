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

package monix.execution.internal

import monix.execution.internal.collection.ChunkedArrayQueue
import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

private[execution] class Trampoline(underlying: ExecutionContext) {
  private def makeQueue(): ChunkedArrayQueue[Runnable] = ChunkedArrayQueue[Runnable](chunkSize = 16)
  private[this] var immediateQueue = makeQueue()
  private[this] var withinLoop = false

  def startLoop(runnable: Runnable): Unit = {
    withinLoop = true
    try immediateLoop(runnable) finally {
      withinLoop = false
    }
  }

  final def execute(runnable: Runnable): Unit = {
    if (!withinLoop) {
      startLoop(runnable)
    } else {
      immediateQueue.enqueue(runnable)
    }
  }

  protected final def forkTheRest(): Unit = {
    final class ResumeRun(head: Runnable, rest: ChunkedArrayQueue[Runnable])
      extends Runnable {

      def run(): Unit = {
        immediateQueue.enqueueAll(rest)
        immediateLoop(head)
      }
    }

    val head = immediateQueue.dequeue()
    if (head ne null) {
      val rest = immediateQueue
      immediateQueue = makeQueue()
      underlying.execute(new ResumeRun(head, rest))
    }
  }

  @tailrec
  private final def immediateLoop(task: Runnable): Unit = {
    try {
      task.run()
    } catch {
      case ex: Throwable =>
        forkTheRest()
        if (NonFatal(ex)) underlying.reportFailure(ex)
        else throw ex
    }

    val next = immediateQueue.dequeue()
    if (next ne null) immediateLoop(next)
  }
}
