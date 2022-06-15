/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import java.util.concurrent.ForkJoinPool.{ForkJoinWorkerThreadFactory, ManagedBlocker}
import java.util.concurrent.{ForkJoinPool, ForkJoinWorkerThread, ThreadFactory}

import monix.execution.internal.forkJoin.DynamicWorkerThreadFactory.EmptyBlockContext

import scala.concurrent.{BlockContext, CanAwait}

// Implement BlockContext on FJP threads
private[monix] final class DynamicWorkerThreadFactory(
  prefix: String,
  uncaught: Thread.UncaughtExceptionHandler,
  daemonic: Boolean)
  extends ThreadFactory with ForkJoinWorkerThreadFactory {

  require(prefix ne null, "DefaultWorkerThreadFactory.prefix must be non null")

  def wire[T <: Thread](thread: T): T = {
    thread.setDaemon(daemonic)
    thread.setUncaughtExceptionHandler(uncaught)
    thread.setName(prefix + "-" + thread.getId)
    thread
  }

  def newThread(runnable: Runnable): Thread =
    wire(new Thread(runnable))

  def newThread(fjp: ForkJoinPool): ForkJoinWorkerThread =
    wire(new ForkJoinWorkerThread(fjp) with BlockContext {
      final override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
        var result: T = null.asInstanceOf[T]
        ForkJoinPool.managedBlock(new ManagedBlocker {
          @volatile
          private[this] var isDone = false
          def isReleasable = isDone

          def block(): Boolean = {
            result =
              try {
                // When we block, switch out the BlockContext temporarily so that nested
                // blocking does not created N new Threads
                BlockContext.withBlockContext(EmptyBlockContext) { thunk }
              } finally {
                isDone = true
              }
            true
          }
        })

        result
      }
    })

}

private[monix] object DynamicWorkerThreadFactory {
  /** Reusable instance that doesn't do anything special. */
  private object EmptyBlockContext extends BlockContext {
    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T =
      thunk
  }
}
