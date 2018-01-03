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
package instances

import cats.effect.Async

/** Cats type class instances for [[monix.eval.Task Task]]
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
    Task.unsafeCreate { (_, cb) => k(r => cb(r)) }
}

/** Default and reusable instance for [[CatsAsyncForTask]].
  *
  * Globally available in scope, as it is returned by
  * [[monix.eval.Task.catsAsync Task.catsAsync]].
  */
object CatsAsyncForTask extends CatsAsyncForTask
