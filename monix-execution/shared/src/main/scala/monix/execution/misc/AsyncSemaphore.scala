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

package monix.execution.misc

import monix.execution.atomic.AtomicAny
import monix.execution.atomic.PaddingStrategy.LeftRight128
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}

/** The `AsyncSemaphore` is an asynchronous semaphore implementation that
  * limits the parallelism on `Future` execution.
  *
  * The following example instantiates a semaphore with a
  * maximum parallelism of 10:
  *
  * {{{
  *   val semaphore = AsyncSemaphore(maxParallelism = 10)
  *
  *   def makeRequest(r: HttpRequest): Future[HttpResponse] = ???
  *
  *   // For such a task no more than 10 requests
  *   // are allowed to be executed in parallel.
  *   val future = semaphore.greenLight(() => makeRequest(???))
  * }}}
  */
final class AsyncSemaphore private (maxParallelism: Int)
  extends Serializable {

  import AsyncSemaphore.State
  require(maxParallelism > 0, "parallelism > 0")

  private[this] val stateRef =
    AtomicAny.withPadding(AsyncSemaphore.initialState, LeftRight128)

  /** Returns the number of active tasks that are holding on
    * to the available permits.
    */
  def activeCount: Int =
    stateRef.get.activeCount

  /** Returns a new future, ensuring that the given source
    * acquires an available permit from the semaphore before
    * it is executed.
    *
    * The returned future also takes care of resource handling,
    * releasing its permit after being complete.
    *
    * @param f is a function returning the `Future` instance we
    *        want to evaluate after we get the permit from the
    *        semaphore
    */
  def greenLight[A](f: () => Future[A])(implicit ec: ExecutionContext): Future[A] =
    acquire().flatMap { _ =>
      val result = f()
      result.onComplete(_ => release())
      result
    }

  /** Internal. Triggers a permit acquisition,
    * returning a future that will complete when a
    * permit gets acquired.
    */
  @tailrec def acquire(): Future[Unit] = {
    stateRef.get match {
      case current @ State(activeCount, _) =>
        if (activeCount < maxParallelism) {
          val update = current.activateOne()

          if (!stateRef.compareAndSet(current, update))
            acquire() // retry
          else
            AsyncSemaphore.availablePermit
        }
        else {
          val p = Promise[Unit]()
          val update = current.addPromise(p)
          if (!stateRef.compareAndSet(current, update))
            acquire() // retry
          else
            p.future
        }
    }
  }

  /** Releases a permit, returning it to the pool.
    *
    * If there are consumers waiting on permits being available,
    * then the first in the queue will be selected and given
    * a permit immediately.
    */
  @tailrec def release(): Unit = {
    stateRef.get match {
      case current @ State(activeCount, promises) =>
        val (p, newPromises) =
          if (promises.nonEmpty) promises.dequeue else (null, promises)
        val newActiveCount =
          if (p != null) activeCount else activeCount - 1
        val update =
          State(newActiveCount, newPromises)

        if (!stateRef.compareAndSet(current, update))
          release() // retry
        else if (p != null)
          p.trySuccess(())
    }
  }
}

object AsyncSemaphore {
  /** Builder for [[AsyncSemaphore]].
    *
    * @param maxParallelism represents the number of tasks allowed for
    *        parallel execution
    */
  def apply(maxParallelism: Int): AsyncSemaphore =
    new AsyncSemaphore(maxParallelism)

  /** Internal. Reusable `Future` reference. */
  private final val availablePermit =
    Future.successful(())
  /** Internal. Reusable initial state. */
  private final val initialState: State =
    State(0, Queue.empty)

  /** Internal. For keeping the state of our
    * [[AsyncSemaphore]] in an atomic reference.
    */
  private final case class State(
    activeCount: Int,
    promises: Queue[Promise[Unit]]) {

    def activateOne(): State =
      copy(activeCount = activeCount + 1)
    def addPromise(p: Promise[Unit]): State =
      copy(promises = promises.enqueue(p))
  }
}
