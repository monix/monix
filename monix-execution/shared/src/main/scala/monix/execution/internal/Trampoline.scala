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

import monix.execution.internal.Trampoline.{ResumeRun, TrampolineEC}
import monix.execution.internal.collection.ChunkedArrayQueue

import scala.annotation.tailrec
import scala.concurrent.{ BlockContext, CanAwait, ExecutionContext }
import scala.util.control.NonFatal

private[execution] class Trampoline(
  private val fallbackTrampoline: Option[() => Trampoline] = None,
) {
  private var immediateQueue: ChunkedArrayQueue[Runnable] = Trampoline.makeQueue()
  private var withinLoop: Boolean = false

  def startLoop(runnable: Runnable, ec: TrampolineEC): Unit = {
    withinLoop = true
    try immediateLoop(runnable, ec)
    finally {
      withinLoop = false
    }
  }

  final def execute(runnable: Runnable, ec: TrampolineEC): Unit = {
    if (!withinLoop) {
      startLoop(runnable, ec)
    } else {
      immediateQueue.enqueue(runnable)
    }
  }

  final def enqueueAll(chunkedArrayQueue: ChunkedArrayQueue[Runnable]): Unit =
    immediateQueue.enqueueAll(chunkedArrayQueue)

  private def continueExecution(ec: TrampolineEC): Unit = {
    val head = immediateQueue.dequeue()
    if (head ne null) {
      if (ec.isImmediate) {
        immediateLoop(head, ec)
      } else {
        forkTheRest(head, ec)
      }
    }
  }

  private def forkTheRest(head: Runnable, ec: TrampolineEC): Unit = {
    val rest = immediateQueue.shallowCopy()
    immediateQueue = Trampoline.makeQueue()
    ec.execute(new ResumeRun(head, rest, ec, trampolineForResume()))
  }

  /**
   * Computation should resume on a trampoline.
   *
   * In a multithreaded environment (JVM) it should be a thread-local trampoline of the thread that resumes the
   * computation. Using trampoline of the previous thread is unsafe because it might lead to concurrent modification
   * of the underlying [[ChunkedArrayQueue]], which is not thread-safe - at that point the previous thread
   * might have already entered another run loop.
   *
   * In a singlethreaded environment (SJS) computation will resume on the same trampoline.
   */
  private def trampolineForResume() =
    fallbackTrampoline.getOrElse(() => this)

  @tailrec
  private final def immediateLoop(task: Runnable, ec: TrampolineEC): Unit = {
    try {
      task.run()
    } catch {
      case ex: Throwable =>
        continueExecution(ec)
        if (NonFatal(ex)) ec.reportFailure(ex)
        else throw ex
    }

    val next = immediateQueue.dequeue()
    if (next ne null) immediateLoop(next, ec)
  }

  protected final def trampolineContext(parentContext: BlockContext, ec: TrampolineEC): BlockContext =
    new BlockContext {
      def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
        // In case of blocking, execute all scheduled local tasks on
        // a separate thread, otherwise we could end up with a dead-lock
        continueExecution(ec)
        if (parentContext ne null) {
          parentContext.blockOn(thunk)
        } else {
          thunk
        }
      }
    }
}

private[execution] object Trampoline {

  /** ExecutionContext backing the [[Trampoline]]. */
  trait TrampolineEC {
    def execute(runnable: Runnable): Unit
    def reportFailure(e: Throwable): Unit
    def isImmediate: Boolean
  }

  /** Forking ExecutionContext backing the [[Trampoline]]. */
  trait ForkingEC extends TrampolineEC {
    def isImmediate: Boolean = false
  }

  final case class ForkingECImpl(ec: ExecutionContext) extends ForkingEC {
    override def execute(runnable: Runnable): Unit = ec.execute(runnable)
    override def reportFailure(e: Throwable): Unit = ec.reportFailure(e)
  }

  /** Immediate ExecutionContext that executes everything on the current thread.
   *  Used for optimization of the run loop.
   */
  object ImmediateEC extends TrampolineEC {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(e: Throwable): Unit = throw e
    override def isImmediate: Boolean = true
  }

  private final class ResumeRun(
    head: Runnable,
    rest: ChunkedArrayQueue[Runnable],
    ec: TrampolineEC,
    fallbackTrampoline: () => Trampoline,
  ) extends Runnable {

    def run(): Unit = {
      val trampoline = fallbackTrampoline()
      trampoline.enqueueAll(rest)
      // Underlying Ec might have scheduled ResumeRun to execute on the same thread, we still need to check if we're
      // already in the loop - thus execute.
      trampoline.execute(head, ec)
    }
  }

  private def makeQueue(): ChunkedArrayQueue[Runnable] = ChunkedArrayQueue[Runnable](chunkSize = 16)
}
