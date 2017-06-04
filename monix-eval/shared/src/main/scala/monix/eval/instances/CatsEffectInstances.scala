/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval.instances

import cats.effect.{Effect, IO}
import monix.eval.{Callback, Task}
import monix.execution.Scheduler

/** Specification for Cats type classes, to be implemented by
  * types that can execute asynchronous computations and that
  * yield exactly one result (e.g. [[Task]]).
  */
trait CatsEffectInstances[F[_]] extends Effect[F]

object CatsEffectInstances {
  /** Cats type class instances for [[Task]]. */
  class ForTask(implicit s: Scheduler)
    extends CatsAsyncInstances.ForTask with CatsEffectInstances[Task] {

    override def runAsync[A](fa: Task[A])(cb: (Either[Throwable, A]) => IO[Unit]): IO[Unit] =
      runAsyncImpl(fa)(cb)
  }

  /** Cats type class instances for [[Task Tasks]] that have
    * non-deterministic effects in their applicative.
    */
  class ForParallelTask(implicit s: Scheduler)
    extends CatsAsyncInstances.ForParallelTask with CatsEffectInstances[Task] {

    override def runAsync[A](fa: Task[A])(cb: (Either[Throwable, A]) => IO[Unit]): IO[Unit] =
      runAsyncImpl(fa)(cb)
  }

  // Reusable `runAsync` implementation
  private def runAsyncImpl[A](fa: Task[A])
    (cb: (Either[Throwable, A]) => IO[Unit])
    (implicit s: Scheduler): IO[Unit] = {

    IO(fa.runAsync(new Callback[A] {
      def onSuccess(value: A): Unit =
        cb(Right(value)).unsafeRunAsync(noop)
      def onError(ex: Throwable): Unit =
        cb(Left(ex)).unsafeRunAsync(noop)
    }))
  }

  /** Reusable reference to an empty callback. */
  private val noop = (_: Either[Throwable, Any]) => ()
}