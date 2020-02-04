/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution

import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.atomic.PaddingStrategy
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.GenericSemaphore.Listener
import monix.execution.internal.GenericSemaphore
import monix.execution.schedulers.TrampolineExecutionContext.immediate

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

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
final class AsyncSemaphore private (provisioned: Long, ps: PaddingStrategy)
  extends GenericSemaphore[Cancelable](provisioned, ps) {

  require(provisioned >= 0, "provisioned >= 0")
  import AsyncSemaphore.executionContext

  protected def emptyCancelable: Cancelable =
    Cancelable.empty
  protected def makeCancelable(f: (Listener[Unit]) => Unit, p: Listener[Unit]): Cancelable =
    new Cancelable { def cancel() = f(p) }

  /** Returns the number of permits currently available. Always non-negative.
    *
    * The protocol is unsafe, the semaphore is used in concurrent settings
    * and thus the value returned isn't stable or reliable. Use with care.
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def available(): Long = unsafeAvailable()

  /** Obtains a snapshot of the current count. Can be negative.
    *
    * Like [[available]] when permits are available but returns the
    * number of permits callers are waiting for when there are no permits
    * available.
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def count(): Long = unsafeCount()

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
  @UnsafeBecauseImpure
  def withPermit[A](f: () => Future[A]): CancelableFuture[A] =
    withPermitN(1)(f)

  /** Returns a new future, ensuring that the given source
    * acquires `n` available permits from the semaphore before
    * it is executed.
    *
    * The returned future also takes care of resource handling,
    * releasing its permits after being complete.
    *
    * @param n is the number of permits required for the given
    *        function to be executed
    *
    * @param f is a function returning the `Future` instance we
    *        want to evaluate after we get the permit from the
    *        semaphore
    */
  @UnsafeBecauseImpure
  def withPermitN[A](n: Long)(f: () => Future[A]): CancelableFuture[A] =
    acquireN(n).flatMap { _ =>
      val result =
        try f()
        catch { case NonFatal(e) => Future.failed(e) }
      FutureUtils.transform[A, A](result, r => { releaseN(n); r })
    }

  /** Acquires a single permit. Alias for `[[acquireN]](1)`.
    *
    * @see [[withPermit]], the preferred way to acquire and release
    * @see [[acquireN]] for a version that can acquire multiple permits
    */
  @UnsafeBecauseImpure
  def acquire(): CancelableFuture[Unit] = acquireN(1)

  /** Acquires `n` permits.
    *
    * The returned effect semantically blocks until all requested permits are
    * available. Note that acquires are satisfied in strict FIFO order, so given
    * an `AsyncSemaphore` with 2 permits available, an `acquireN(3)` will
    * always be satisfied before a later call to `acquireN(1)`.
    *
    * @see [[withPermit]], the preferred way to acquire and release
    * @see [[acquire]] for a version acquires a single permit
    *
    * @param n number of permits to acquire - must be >= 0
    *
    * @return a future that will complete when the acquisition has succeeded
    *         or that can be cancelled, removing the listener from the queue
    *         (to prevent memory leaks in race conditions)
    */
  @UnsafeBecauseImpure
  def acquireN(n: Long): CancelableFuture[Unit] = {
    if (unsafeTryAcquireN(n)) {
      CancelableFuture.unit
    } else {
      val p = Promise[Unit]()
      unsafeAcquireN(n, Callback.fromPromise(p)) match {
        case Cancelable.empty => CancelableFuture.unit
        case c => CancelableFuture(p.future, c)
      }
    }
  }

  /** Alias for `[[tryAcquireN]](1)`.
    *
    * The protocol is unsafe, because with the "try*" methods the user needs a
    * firm grasp of what race conditions are and how they manifest and usage of
    * such methods can lead to very fragile logic.
    *
    * @see [[tryAcquireN]] for the version that can acquire multiple permits
    * @see [[acquire]] for the version that can wait for acquisition
    * @see [[withPermit]] the preferred way to acquire and release
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryAcquire(): Boolean = tryAcquireN(1)

  /** Acquires `n` permits now and returns `true`, or returns `false`
    * immediately. Error if `n < 0`.
    *
    * The protocol is unsafe, because with the "try*" methods the user needs a
    * firm grasp of what race conditions are and how they manifest and usage of
    * such methods can lead to very fragile logic.
    *
    * @see [[tryAcquire]] for the alias that acquires a single permit
    * @see [[acquireN]] for the version that can wait for acquisition
    * @see [[withPermit]], the preferred way to acquire and release
    *
    * @param n number of permits to acquire - must be >= 0
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryAcquireN(n: Long): Boolean = unsafeTryAcquireN(n)

  /** Releases a permit, returning it to the pool.
    *
    * If there are consumers waiting on permits being available,
    * then the first in the queue will be selected and given
    * a permit immediately.
    *
    * @see [[withPermit]], the preferred way to acquire and release
    */
  @UnsafeBecauseImpure
  def release(): Unit = releaseN(1)

  /** Releases `n` permits, potentially unblocking up to `n`
    * outstanding acquires.
    *
    * @see [[withPermit]], the preferred way to acquire and release
    *
    * @param n number of permits to release - must be >= 0
    */
  @UnsafeBecauseImpure
  def releaseN(n: Long): Unit = unsafeReleaseN(n)

  /** Returns a future that will be complete when the specified
    * number of permits are available.
    *
    * The protocol is unsafe because by the time the returned
    * future completes, some other process might have already
    * acquired the available permits and thus usage of `awaitAvailable`
    * can lead to fragile concurrent logic. Use with care.
    *
    * Can be useful for termination logic, for example to execute
    * a piece of logic once all available permits have been released.
    *
    * @param n is the number of permits waited on
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def awaitAvailable(n: Long): CancelableFuture[Unit] = {
    val p = Promise[Unit]()
    unsafeAwaitAvailable(n, Callback.fromPromise(p)) match {
      case Cancelable.empty => CancelableFuture.unit
      case c => CancelableFuture(p.future, c)
    }
  }
}

object AsyncSemaphore {
  /** Builder for [[AsyncSemaphore]].
    *
    * @param provisioned is the number of permits initially available
    *
    * @param ps is an optional padding strategy for avoiding the
    *        "false sharing problem", a common JVM effect when multiple threads
    *        read and write in shared variables
    */
  def apply(provisioned: Long, ps: PaddingStrategy = NoPadding): AsyncSemaphore =
    new AsyncSemaphore(provisioned, ps)

  /** Used internally for flatMapping futures. */
  private implicit def executionContext: ExecutionContext =
    immediate
}
