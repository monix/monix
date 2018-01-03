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

import cats.arrow.FunctionK
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
class CatsParallelForTask extends Parallel[Task, Task] {
  import CatsParallelForTask.{NondetApplicative, taskId}

  override def applicative: Applicative[Task] =
    NondetApplicative
  override def monad: Monad[Task] =
    CatsAsyncForTask
  override def sequential: Task ~> Task =
    taskId
  override def parallel: Task ~> Task =
    taskId
}

object CatsParallelForTask extends CatsParallelForTask {
  private object NondetApplicative extends Applicative[Task] {
    override def ap[A, B](ff: Task[(A) => B])(fa: Task[A]): Task[B] =
      Task.mapBoth(ff, fa)(_ (_))
    override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
      Task.mapBoth(fa, fb)(f)
    override def product[A, B](fa: Task[A], fb: Task[B]): Task[(A, B)] =
      Task.mapBoth(fa, fb)((_, _))
    override def pure[A](a: A): Task[A] =
      Task.now(a)
    override val unit: Task[Unit] =
      Task.now(())
    override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
      fa.map(f)
  }

  private val taskId: Task ~> Task =
    FunctionK.id[Task]
}
