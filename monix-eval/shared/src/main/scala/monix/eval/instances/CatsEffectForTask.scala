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

package monix.eval.instances

import cats.effect.{Effect, IO}
import monix.eval.{Callback, Task}
import monix.execution.Scheduler

/** Cats type class instances for [[monix.eval.Task Task]]
  * for  `cats.effect.Effect` (and implicitly for
  * `Applicative`, `Monad`, `MonadError`, `Sync`, etc).
  *
  * Note this is a separate class from [[CatsAsyncForTask]], because we 
  * need an implicit [[monix.execution.Scheduler Scheduler]] in scope 
  * in order to trigger the execution of a `Task`. However we cannot
  * inherit directly from `CatsAsyncForTask`, because it would create 
  * conflicts due to that one having a higher priority but being a 
  * super-type.
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
class CatsEffectForTask(implicit ec: Scheduler)
  extends CatsBaseForTask with Effect[Task] {

  override def runAsync[A](fa: Task[A])
    (cb: Either[Throwable, A] => IO[Unit]): IO[Unit] = {

    import CatsEffectForTask.noop
    IO(fa.runAsync(new Callback[A] {
      def onSuccess(value: A): Unit =
        cb(Right(value)).unsafeRunAsync(noop)
      def onError(ex: Throwable): Unit =
        cb(Left(ex)).unsafeRunAsync(noop)
    }))
  }

  /** We need to mixin [[CatsAsyncForTask]], because if we
    * inherit directly from it, the implicits priorities don't
    * work, triggering conflicts.
    */
  private[this] val F = CatsAsyncForTask

  override def delay[A](thunk: => A): Task[A] =
    F.delay(thunk)
  override def suspend[A](fa: => Task[A]): Task[A] =
    F.suspend(fa)
  override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): Task[A] =
    F.async(k)
}

object CatsEffectForTask {
  /** Reusable reference to an empty callback. */
  private val noop = (_: Either[Throwable, Any]) => ()
}
