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

package monix.eval
package instances

import cats.effect.{ Async, CancelToken, Concurrent, ExitCase }
import monix.eval.internal.TaskCreate

/** Cats type class instance of [[monix.eval.Task Task]]
  * for  `cats.effect.Async` and `CoflatMap` (and implicitly for
  * `Applicative`, `Monad`, `MonadError`, etc).
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
class CatsAsyncForTask extends CatsBaseForTask with Async[Task] {
  override def delay[A](thunk: => A): Task[A] =
    Task.eval(thunk)
  override def suspend[A](fa: => Task[A]): Task[A] =
    Task.defer(fa)
  override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): Task[A] =
    TaskCreate.async(k)
  override def bracket[A, B](acquire: Task[A])(use: A => Task[B])(release: A => Task[Unit]): Task[B] =
    acquire.bracket(use)(release)
  override def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(
    release: (A, ExitCase[Throwable]) => Task[Unit]
  ): Task[B] =
    acquire.bracketCase(use)(release)
  override def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    Task.asyncF(k)
  override def guarantee[A](acquire: Task[A])(finalizer: Task[Unit]): Task[A] =
    acquire.guarantee(finalizer)
  override def guaranteeCase[A](acquire: Task[A])(finalizer: ExitCase[Throwable] => Task[Unit]): Task[A] =
    acquire.guaranteeCase(finalizer)
}

/** Cats type class instance of [[monix.eval.Task Task]]
  * for  `cats.effect.Concurrent`.
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
class CatsConcurrentForTask extends CatsAsyncForTask with Concurrent[Task] {
  override def cancelable[A](k: (Either[Throwable, A] => Unit) => CancelToken[Task]): Task[A] =
    TaskCreate.cancelableEffect(k)
  override def uncancelable[A](fa: Task[A]): Task[A] =
    fa.uncancelable
  override def start[A](fa: Task[A]): Task[Fiber[A]] =
    fa.start
  override def racePair[A, B](fa: Task[A], fb: Task[B]): Task[Either[(A, Fiber[B]), (Fiber[A], B)]] =
    Task.racePair(fa, fb)
  override def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    Task.race(fa, fb)
}

/** Default and reusable instance for [[CatsConcurrentForTask]].
  *
  * Globally available in scope, as it is returned by
  * [[monix.eval.Task.catsAsync Task.catsAsync]].
  */
object CatsConcurrentForTask extends CatsConcurrentForTask
