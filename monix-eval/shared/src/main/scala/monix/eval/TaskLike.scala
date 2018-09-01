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

import cats.effect.{ConcurrentEffect, Effect, IO}
import cats.implicits._
import cats.{Eval, Monad}
import monix.eval.internal.TaskConversions

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

/** A lawless type class that provides conversions to [[Task]].
  *
  * Sample:
  * {{{
  *   // Conversion from cats.Eval
  *   import cats.Eval
  *
  *   val source0 = Eval.always(1 + 1)
  *   val task0 = ToTask[Eval].toTask(source)
  *
  *   // Conversion from Future
  *   import scala.concurrent.Future
  *
  *   val source1 = Future(1 + 1)
  *   val task1 = ToTask[Future].toTask(source)
  *
  *   // Conversion from IO
  *   import cats.effect.IO
  *
  *   val source2 = IO(1 + 1)
  *   val task2 = ToTask[IO].toTask(source2)
  * }}}
  *
  * This is an alternative to usage of `cats.effect.Effect`
  * where the internals are specialized to `Task` anyway, like for
  * example the implementation of `monix.reactive.Observable`.
  */
@implicitNotFound("""Cannot find implicit value for TaskLike[${F}].
Building this implicit value might depend on having an implicit
s.c.ExecutionContext in scope, a Scheduler or some equivalent type.""")
trait TaskLike[F[_]] {
  /**
    * `ToTask` has a `Monad` restriction, expressed via composition
    * instead of inheritance.
    */
  def monad: Monad[F]

  /**
    * Converts from `F[A]` to `Task[A]`, preserving referential
    * transparency if `F[_]` is a pure data type and preserving
    * interruptibility if the source is cancelable.
    */
  def toTask[A](fa: F[A]): Task[A]
}

object TaskLike extends ToTaskImplicits0 {
  /**
    * Returns the available instance for `F`.
    */
  def apply[F[_]](implicit F: TaskLike[F]): TaskLike[F] = F

  /**
    * Instance for `Task`, returning same reference.
    */
  implicit val fromTask: TaskLike[Task] =
    new TaskLike[Task] {
      val monad = Monad[Task]
      def toTask[A](fa: Task[A]): Task[A] = fa
    }

  /**
    * Converts to `Task` from [[scala.concurrent.Future]].
    */
  implicit def fromFuture(implicit ec: ExecutionContext): TaskLike[Future] =
    new TaskLike[Future] {
      val monad = catsStdInstancesForFuture
      def toTask[A](fa: Future[A]): Task[A] =
        Task.fromFuture(fa)
    }

  /**
    * Converts to `Task` from [[Coeval]].
    */
  implicit val fromCoeval: TaskLike[Coeval] =
    new TaskLike[Coeval] {
      val monad = Monad[Coeval]
      def toTask[A](fa: Coeval[A]): Task[A] =
        Task.coeval(fa)
    }

  /**
    * Converts to `Task` from `cats.effect.Eval`.
    */
  implicit val fromEval: TaskLike[Eval] =
    new TaskLike[Eval] {
      val monad = Monad[Eval]
      def toTask[A](fa: Eval[A]): Task[A] =
        Task.fromEval(fa)
    }

  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/datatypes/io.html cats.effect.IO]].
    */
  implicit val fromIO: TaskLike[IO] =
    new TaskLike[IO] {
      val monad = Monad[IO]
      def toTask[A](fa: IO[A]): Task[A] =
        Task.fromIO(fa)
    }
}

private[eval] abstract class ToTaskImplicits0 extends ToTaskImplicits1 {
  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.ConcurrentEffect]].
    */
  implicit def fromConcurrentEffect[F[_]](implicit F: ConcurrentEffect[F]): TaskLike[F] =
    new TaskLike[F] {
      val monad = F
      def toTask[A](fa: F[A]): Task[A] =
        TaskConversions.fromConcurrentEffect(fa)
    }
}

private[eval] abstract class ToTaskImplicits1 {
  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.Async]].
    */
  implicit def fromEffect[F[_]](implicit F: Effect[F]): TaskLike[F] =
    new TaskLike[F] {
      val monad = F
      def toTask[A](fa: F[A]): Task[A] =
        Task.fromEffect(fa)
    }
}
