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

package monix.eval

import monix.execution.Cancelable
import monix.execution.misc.AsyncSemaphore

/** The `TaskSemaphore` is an asynchronous semaphore implementation that
  * limits the parallelism on task execution.
  *
  * The following example instantiates a semaphore with a
  * maximum parallelism of 10:
  *
  * {{{
  *   val semaphore = TaskSemaphore(maxParallelism = 10)
  *
  *   def makeRequest(r: HttpRequest): Task[HttpResponse] = ???
  *
  *   // For such a task no more than 10 requests
  *   // are allowed to be executed in parallel.
  *   val task = semaphore.greenLight(makeRequest(???))
  * }}}
  */
final class TaskSemaphore private (maxParallelism: Int) extends Serializable {
  require(maxParallelism > 0, "parallelism > 0")

  private[this] val semaphore = AsyncSemaphore(maxParallelism)

  /** Returns the number of active tasks that are holding on
    * to the available permits.
    */
  def activeCount: Coeval[Int] =
    Coeval.eval(semaphore.activeCount)

  /** Creates a new task ensuring that the given source
    * acquires an available permit from the semaphore before
    * it is being executed.
    *
    * The returned task also takes care of resource handling,
    * releasing its permit after being complete.
    */
  def greenLight[A](fa: Task[A]): Task[A] = {
    // Inlining doOnFinish + doOnCancel
    val taskWithRelease = Task.unsafeCreate[A] { (context, cb) =>
      implicit val s = context.scheduler
      val c = Cancelable(() => semaphore.release())
      val conn = context.connection
      conn.push(c)

      // Light asynchronous boundary
      Task.unsafeStartTrampolined(fa, context, new Callback[A] {
        def onSuccess(value: A): Unit = {
          conn.pop()
          semaphore.release()
          cb.asyncOnSuccess(value)
        }

        def onError(ex: Throwable): Unit = {
          conn.pop()
          semaphore.release()
          cb.asyncOnError(ex)
        }
      })
    }

    acquire.flatMap { _ => taskWithRelease }
  }

  /** Triggers a permit acquisition, returning a task
    * that upon evaluation will only complete after a permit
    * has been acquired.
    */
  val acquire: Task[Unit] =
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler
      val f = semaphore.acquire()

      if (f.isCompleted)
        cb.asyncApply(f.value.get)
      else {
        val conn = context.connection
        conn.push(f)
        f.onComplete { result =>
          // Async boundary should trigger frame reset
          context.frameRef.reset()
          conn.pop()
          cb(result)
        }
      }
    }

  /** Returns a task that upon evaluation will release a permit,
    * returning it to the pool.
    *
    * If there are consumers waiting on permits being available,
    * then the first in the queue will be selected and given
    * a permit immediately.
    */
  val release: Task[Unit] =
    Task.eval(semaphore.release())

  /** Returns a task, that upon evaluation will be complete when
    * all the currently acquired permits are released, or in other
    * words when the [[activeCount]] is zero.
    *
    * This also means that we are going to wait for the
    * acquisition and release of all enqueued promises as well.
    */
  val awaitAllReleased: Task[Unit] =
    Task.defer(Task.fromFuture(semaphore.awaitAllReleased()))
}

object TaskSemaphore {
  /** Builder for [[TaskSemaphore]].
    *
    * @param maxParallelism represents the number of tasks
    *        allowed for parallel execution
    */
  def apply(maxParallelism: Int): TaskSemaphore =
    new TaskSemaphore(maxParallelism)
}
