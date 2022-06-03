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

package monix.eval
package instances

import cats.effect.{ Fiber => _, _ }
import monix.eval.internal.TaskEffect
import monix.execution.Scheduler

/** Cats type class instances of [[monix.eval.Task Task]] for
  * `cats.effect.Effect` (and implicitly for `Applicative`, `Monad`,
  * `MonadError`, `Sync`, etc).
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
class CatsEffectForTask(implicit s: Scheduler, opts: Task.Options) extends CatsBaseForTask with Effect[Task] {

  /** We need to mixin [[CatsAsyncForTask]], because if we
    * inherit directly from it, the implicits priorities don't
    * work, triggering conflicts.
    */
  private[this] val F = CatsConcurrentForTask

  override def runAsync[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
    TaskEffect.runAsync(fa)(cb)
  override def delay[A](thunk: => A): Task[A] =
    F.delay(thunk)
  override def suspend[A](fa: => Task[A]): Task[A] =
    F.suspend(fa)
  override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): Task[A] =
    F.async(k)
  override def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    F.asyncF(k)
  override def bracket[A, B](acquire: Task[A])(use: A => Task[B])(release: A => Task[Unit]): Task[B] =
    F.bracket(acquire)(use)(release)
  override def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(
    release: (A, ExitCase[Throwable]) => Task[Unit]
  ): Task[B] =
    F.bracketCase(acquire)(use)(release)
}

/** Cats type class instances of [[monix.eval.Task Task]] for
  * `cats.effect.ConcurrentEffect`.
  *
  * Note this is a separate class from [[CatsConcurrentForTask]], because
  * we need an implicit [[monix.execution.Scheduler Scheduler]] in scope
  * in order to trigger the execution of a `Task`. However we cannot
  * inherit directly from `CatsConcurrentForTask`, because it would create
  * conflicts due to that one having a higher priority but being a
  * super-type.
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
class CatsConcurrentEffectForTask(implicit s: Scheduler, opts: Task.Options)
  extends CatsEffectForTask with ConcurrentEffect[Task] {

  /** We need to mixin [[CatsAsyncForTask]], because if we
    * inherit directly from it, the implicits priorities don't
    * work, triggering conflicts.
    */
  private[this] val F = CatsConcurrentForTask

  override def runCancelable[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[Task]] =
    TaskEffect.runCancelable(fa)(cb)
  override def cancelable[A](k: (Either[Throwable, A] => Unit) => CancelToken[Task]): Task[A] =
    F.cancelable(k)
  override def uncancelable[A](fa: Task[A]): Task[A] =
    F.uncancelable(fa)
  override def start[A](fa: Task[A]): Task[Fiber[A]] =
    F.start(fa)
  override def racePair[A, B](fa: Task[A], fb: Task[B]): Task[Either[(A, Fiber[B]), (Fiber[A], B)]] =
    F.racePair(fa, fb)
  override def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    F.race(fa, fb)
}
