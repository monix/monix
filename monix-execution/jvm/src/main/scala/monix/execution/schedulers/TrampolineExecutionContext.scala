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
import scala.concurrent.{BlockContext, CanAwait, ExecutionContext}

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

  private[this] val localContext = TrampolineExecutionContext.localContext
  private[this] val localTasks = new ThreadLocal[List[Runnable]]()

  private[this] val trampolineContext: BlockContext =
    new BlockContext {
      def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
        // In case of blocking, execute all scheduled local tasks on
        // a separate thread, otherwise we could end up with a dead-lock
        forkTheRest(Nil)
        thunk
      }
    }

  override def execute(runnable: Runnable): Unit =
    localTasks.get match {
      case null =>
        // Optimal execution happens when we can access
        // BlockContext.contextLocal
        if (localContext ne null)
          startLoopOptimal(runnable)
        else
          startLoopNormal(runnable)

      case some =>
        // If we are already in batching mode, add to stack
        localTasks.set(runnable :: some)
    }

  private def startLoopOptimal(runnable: Runnable): Unit = {
    // If we aren't in local mode yet, start local loop
    localTasks.set(Nil)
    val parentContext = localContext.get()
    try {
      localContext.set(trampolineContext)
      localRunLoop(runnable)
    } finally {
      localContext.set(parentContext)
    }
  }

  private def startLoopNormal(runnable: Runnable): Unit = {
    // If we aren't in local mode yet, start local loop
    localTasks.set(Nil)
    BlockContext.withBlockContext(trampolineContext) {
      localRunLoop(runnable)
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
        underlying.execute(new ResumeRun(head, tail))
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

  /** Returns the `localContext`, allowing us to bypass calling
    * `BlockContext.withBlockContext`, as an optimization trick.
    */
  private final val localContext: ThreadLocal[BlockContext] = {
    try {
      val methods = BlockContext.getClass.getDeclaredMethods
        .filter(m => m.getParameterCount == 0 && m.getReturnType == classOf[ThreadLocal[_]])
        .toList

      methods match {
        case m :: Nil =>
          m.setAccessible(true)
          m.invoke(BlockContext).asInstanceOf[ThreadLocal[BlockContext]]
        case _ =>
          throw new NoSuchMethodError("BlockContext.contextLocal")
      }
    } catch {
      case _: NoSuchMethodError => null
      case _: SecurityException => null
      case NonFatal(_) => null
    }
  }
}