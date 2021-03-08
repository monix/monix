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

package monix.catnap

import cats.effect.Async
import monix.catnap.internal.AsyncUtils
import monix.execution.Callback
import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.atomic.PaddingStrategy
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.GenericSemaphore
import monix.execution.internal.GenericSemaphore.Listener
import scala.concurrent.Promise

import cats.effect.kernel.{MonadCancel, Poll, Resource}
import cats.~>

/** The `Semaphore` is an asynchronous semaphore implementation that
  * limits the parallelism on task execution.
  *
  * The following example instantiates a semaphore with a
  * maximum parallelism of 10:
  *
  * {{{
  *   import cats.implicits._
  *   import cats.effect.IO
  *
  *
  *   // Dummies for didactic purposes
  *   case class HttpRequest()
  *   case class HttpResponse()
  *   def makeRequest(r: HttpRequest): IO[HttpResponse] = IO(???)
  *
  *   for {
  *     semaphore <- Semaphore[IO](provisioned = 10)
  *     tasks = for (_ <- 0 until 1000) yield {
  *       semaphore.permit.surround(makeRequest(HttpRequest()))
  *     }
  *     // Execute in parallel; note that due to the `semaphore`
  *     // no more than 10 tasks will be allowed to execute in parallel
  *     _ <- tasks.toList.parSequence
  *   } yield ()
  * }}}
  *
  * ==Credits==
  *
  * `Semaphore` is now implementing `cats.effect.Semaphore`, deprecating
  * the old Monix `TaskSemaphore`.
  *
  * The changes to the interface and some implementation details are
  * inspired by the implementation in Cats-Effect, which was ported
  * from FS2.
  */
trait Semaphore[F[_]]
  extends cats.effect.std.Semaphore[F] {

  @UnsafeProtocol
  def available: F[Long]

  @UnsafeProtocol
  def count: F[Long]

  /** Acquires `n` permits.
    *
    * The returned effect semantically blocks until all requested permits are
    * available. Note that acquires are satisfied in strict FIFO order, so given
    * a `Semaphore[F]` with 2 permits available, an `acquireN(3)` will
    * always be satisfied before a later call to `acquireN(1)`.
    *
    * @see [[permits]], the preferred way to acquire and release
    * @see [[acquire]] for a version acquires a single permit
    *
    * @param n number of permits to acquire; must be >= 0
    */
  def acquireN(n: Long): F[Unit]

  /** Acquires a single permit. Alias for `[[acquireN]](1)`.
    *
    * @see [[permit]], the preferred way to acquire and release
    * @see [[acquireN]] for a version that can acquire multiple permits
    */
  def acquire: F[Unit]

  /** Alias for `[[tryAcquireN]](1)`.
    *
    * The protocol is unsafe, because with the "try*" methods the user needs a
    * firm grasp of what race conditions are and how they manifest and usage of
    * such methods can lead to very fragile logic.
    *
    * @see [[tryAcquireN]] for the version that can acquire multiple permits
    * @see [[acquireN]] for the version that can wait for acquisition
    * @see [[permits]] the preferred way to acquire and release
    */
  @UnsafeProtocol
  def tryAcquireN(n: Long): F[Boolean]

  /** Alias for `[[tryAcquireN]](1)`.
    *
    * The protocol is unsafe, because with the "try*" methods the user needs a
    * firm grasp of what race conditions are and how they manifest and usage of
    * such methods can lead to very fragile logic.
    *
    * @see [[tryAcquireN]] for the version that can acquire multiple permits
    * @see [[acquire]] for the version that can wait for acquisition
    * @see [[permit]] the preferred way to acquire and release
    */
  @UnsafeProtocol
  def tryAcquire: F[Boolean]

  /** Releases `n` permits, potentially unblocking up to `n`
    * outstanding acquires.
    *
    * @see [[withPermit]], the preferred way to acquire and release
    *
    * @param n number of permits to release - must be >= 0
    */
  def releaseN(n: Long): F[Unit]

  /** Returns a task that will be complete when the specified
    * number of permits are available.
    *
    * The protocol is unsafe because by the time the returned
    * task completes, some other process might have already
    * acquired the available permits and thus usage of `awaitAvailable`
    * can lead to fragile concurrent logic. Use with care.
    *
    * Can be useful for termination logic, for example to execute
    * a piece of logic once all available permits have been released.
    *
    * @param n is the number of permits waited on
    */
  @UnsafeProtocol
  def awaitAvailable(n: Long): F[Unit]

  // TODO Docs, tests
  val permit: Resource[F, Unit] = permits(1)

  // TODO Docs, tests
  def permits(n: Long): Resource[F, Unit]

  // concretize type to catnap Semaphore
  def mapK[G[_]](f: F ~> G)(implicit G: MonadCancel[G, _]): Semaphore[G]
}

object Semaphore {
  /**
    * Builds a [[Semaphore]] instance.
    *
    * @param provisioned is the number of permits initially available
    *
    * @param ps is an optional padding strategy for avoiding the
    *        "false sharing problem", a common JVM effect when multiple threads
    *        read and write in shared variables
    *
    * @param F is the type class instance required to make `Semaphore` work,
    *        can be either `Concurrent` or `Async` for extra flexibility
    *
    * @param cs is a `ContextShift` instance required in order to introduce
    *        async boundaries after successful `acquire` operations, for safety
    */
  def apply[F[_]](provisioned: Long, ps: PaddingStrategy = NoPadding)(
    implicit F: Async[F]): F[Semaphore[F]] = {

    F.delay(new AsyncSemaphore[F](provisioned, ps))
  }

  /** Builds a [[Semaphore]] instance.
    *
    * '''Unsafe warning:''' this violates referential transparency.
    * Use with care, prefer the pure [[Semaphore.apply]].
    *
    * @param provisioned is the number of permits initially available
    *
    * @param ps is an optional padding strategy for avoiding the
    *        "false sharing problem", a common JVM effect when multiple threads
    *        read and write in shared variables
    *
    * @param F is the type class instance required to make `Semaphore` work,
    *        can be either `Concurrent` or `Async` for extra flexibility
    *
    * @param cs is a `ContextShift` instance required in order to introduce
    *        async boundaries after successful `acquire` operations, for safety
    */
  @UnsafeBecauseImpure
  def unsafe[F[_]](provisioned: Long, ps: PaddingStrategy = NoPadding)(implicit F: Async[F]): Semaphore[F] =
    new AsyncSemaphore[F](provisioned, ps)

  private[Semaphore] final class AsyncSemaphore[F[_]] (provisioned: Long, ps: PaddingStrategy)(implicit F: Async[F])
    extends Semaphore[F] {

    override def available: F[Long] = underlying.available

    override def count: F[Long] = underlying.count

    override def acquireN(n: Long): F[Unit] =
      underlying.acquireN(n)

    override def acquire: F[Unit] = acquireN(1)

    override def tryAcquireN(n: Long): F[Boolean] =
      underlying.tryAcquireN(n)
    @UnsafeProtocol
    override def tryAcquire: F[Boolean] = tryAcquireN(1)

    override def releaseN(n: Long): F[Unit] =
      underlying.releaseN(n)

    override def release: F[Unit] = releaseN(1)

    override def awaitAvailable(n: Long): F[Unit] =
      underlying.awaitAvailable(n)

    override def permits(n: Long): Resource[F, Unit] =
      Resource.makeFull[F, Unit] { poll => poll(acquireN(n)) } { _ => releaseN(n) }

    override def mapK[G[_]](f: F ~> G)(implicit G: MonadCancel[G, _]): Semaphore[G] =
      new Transformed(this, f)

    private[this] val underlying =
      new Semaphore.Impl[F](provisioned, ps)
  }

  private final class Impl[F[_]](provisioned: Long, ps: PaddingStrategy)(
    implicit F: Async[F])
    extends GenericSemaphore[F[Unit]](provisioned, ps) {

    val available: F[Long] = F.delay(unsafeAvailable())
    val count: F[Long] = F.delay(unsafeCount())

    def acquireN(n: Long): F[Unit] =
      F.defer {
        if (unsafeTryAcquireN(n))
          F.unit
        else
          F.flatMap(make[Unit](unsafeAcquireN(n, _)))(bindFork)
      }

    def tryAcquireN(n: Long): F[Boolean] =
      F.delay(unsafeTryAcquireN(n))

    def releaseN(n: Long): F[Unit] =
      F.delay(unsafeReleaseN(n))

    def awaitAvailable(n: Long): F[Unit] =
      F.flatMap(make[Unit](unsafeAwaitAvailable(n, _)))(bindFork)

    protected def emptyCancelable: F[Unit] =
      F.unit
    protected def makeCancelable(f: (Listener[Unit]) => Unit, p: Listener[Unit]): F[Unit] =
      F.delay(f(p))

    private def make[A](k: (Either[Throwable, A] => Unit) => F[Unit]): F[A] =
      AsyncUtils.cancelable(k)(F)

    private[this] val bindFork: (Unit => F[Unit]) =
      _ => F.cede
  }

  private final class Transformed[F[_], G[_]](base: Semaphore[F], f: F ~> G)(
    implicit F: MonadCancel[F, _], G: MonadCancel[G, _]
  )
    extends Semaphore[G] {
    override def available: G[Long] = f(base.available)
    override def count: G[Long] = f(base.count)
    override def acquireN(n: Long): G[Unit] = f(base.acquireN(n))

    override def tryAcquireN(n: Long): G[Boolean] = f(base.tryAcquireN(n))

    override def awaitAvailable(n: Long): G[Unit] = f(base.awaitAvailable(n))

    override def permits(n: Long): Resource[G, Unit] =
      base.permits(n).mapK(f)

    override def releaseN(n: Long): G[Unit] = f(base.releaseN(n))

    override def mapK[H[_]](f: G ~> H)(implicit H: MonadCancel[H, _]): Semaphore[H] =
      new Transformed(this, f)
  }
}
