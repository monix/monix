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

package monix.eval

import monix.eval.Task.Context
import monix.execution.Cancelable
import monix.execution.misc.AsyncSemaphore
import monix.execution.schedulers.TrampolinedRunnable

/** The `TaskSemaphore` is an asynchronous semaphore implementation that
  * limits the parallelism on task execution.
  *
  * The following example instantiates a semaphore with a
  * maximum parallelism of 10:
  *
  * {{{
  *   case class HttpRequest()
  *   case class HttpResponse()
  *
  *   val semaphore = TaskSemaphore(maxParallelism = 10)
  *
  *   def makeRequest(r: HttpRequest): Task[HttpResponse] = ???
  *
  *   // For such a task no more than 10 requests
  *   // are allowed to be executed in parallel.
  *   val task = semaphore
  *     .flatMap(_.greenLight(makeRequest(???)))
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
    val start = (context: Context, cb: Callback[A]) => {
      implicit val s = context.scheduler
      val c = Cancelable(() => semaphore.release())
      val conn = context.connection
      conn.push(c)

      Task.unsafeStartNow(fa, context,
        new Callback[A] with TrampolinedRunnable {
          private[this] var value: A = _
          private[this] var error: Throwable = _

          def onSuccess(value: A): Unit = {
            this.value = value
            s.execute(this)
          }
          def onError(ex: Throwable): Unit = {
            this.error = ex
            s.execute(this)
          }
          def run() = {
            conn.pop()
            semaphore.release()
            val e = error
            if (e eq null) cb.onSuccess(value)
            else cb.onError(e)
          }
        })
    }

    val taskWithRelease = Task.Async(
      start,
      trampolineBefore = true,
      trampolineAfter = false,
      restoreLocals = false)

    // TODO: make sure this works with auto-cancelable tasks, or fix it!
    // Probably needs bracket!
    acquire.flatMap { _ => taskWithRelease }
  }

  /** Triggers a permit acquisition, returning a task
    * that upon evaluation will only complete after a permit
    * has been acquired.
    */
  val acquire: Task[Unit] = {
    val start = (context: Context, cb: Callback[Unit]) => {
      import monix.execution.schedulers.TrampolineExecutionContext.immediate
      implicit val s = context.scheduler
      val f = semaphore.acquire()

      if (f.isCompleted)
        cb(f.value.get)
      else {
        val conn = context.connection
        conn.push(f)
        f.onComplete { result =>
          // Async boundary should trigger frame reset
          context.frameRef.reset()
          conn.pop()
          cb(result)
        }(immediate)
      }
    }
    Task.Async(start, trampolineBefore = false, trampolineAfter = false)
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
    * [[Task]] returned by this operation produces a new
    * [[TaskSemaphore]] each time it is evaluated. To share
    * a semaphore between multiple consumers, pass
    * it as a parameter or use [[Task.memoize]]
    *
    * @param maxParallelism represents the number of tasks
    *        allowed for parallel execution
    */
  def apply(maxParallelism: Int): Task[TaskSemaphore] =
    Task.eval(new TaskSemaphore(maxParallelism))
}
