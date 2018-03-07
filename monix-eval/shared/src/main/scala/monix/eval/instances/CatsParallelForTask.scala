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

import cats.{Applicative, Monad, Parallel, ~>}
import monix.eval.Task

/** `cats.Parallel` type class instance for [[monix.eval.Task Task]].
  *
  * A `cats.Parallel` instances means that `Task` can be used for
  * processing tasks in parallel (with non-deterministic effects
  * ordering).
  *
  * References:
  *
  *  - [[https://typelevel.org/cats/ typelevel/cats]]
  *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
  */
class CatsParallelForTask extends Parallel[Task, Task.Par] {

  override def applicative: Applicative[Task.Par] = CatsParallelForTask.NondetApplicative
  override def monad: Monad[Task] = CatsAsyncForTask

  override val sequential: Task.Par  ~> Task = new (Task.Par ~> Task) {
    def apply[A](fa: Task.Par[A]): Task[A] = Task.Par.unwrap(fa)
  }
  override val parallel: Task ~> Task.Par  = new (Task ~> Task.Par) {
    def apply[A](fa: Task[A]): Task.Par[A] = Task.Par.apply(fa)
  }
}

object CatsParallelForTask extends CatsParallelForTask {
  private object NondetApplicative extends Applicative[Task.Par] {

    import Task.Par.unwrap
    import Task.Par.{apply => par}

    override def ap[A, B](ff: Task.Par[(A) => B])(fa: Task.Par[A]): Task.Par[B] =
      par(Task.mapBoth(unwrap(ff), unwrap(fa))(_ (_)))
    override def map2[A, B, Z](fa: Task.Par[A], fb: Task.Par[B])(f: (A, B) => Z): Task.Par[Z] =
      par(Task.mapBoth(unwrap(fa), unwrap(fb))(f))
    override def product[A, B](fa: Task.Par[A], fb: Task.Par[B]): Task.Par[(A, B)] =
      par(Task.mapBoth(unwrap(fa), unwrap(fb))((_, _)))
    override def pure[A](a: A): Task.Par[A] =
      par(Task.now(a))
    override val unit: Task.Par[Unit] =
      par(Task.now(()))
    override def map[A, B](fa: Task.Par[A])(f: (A) => B): Task.Par[B] =
      par(unwrap(fa).map(f))
  }
}