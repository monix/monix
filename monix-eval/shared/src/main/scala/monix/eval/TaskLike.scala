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

import cats.{Comonad, Eval}
import cats.effect.{ConcurrentEffect, Effect, IO, SyncIO}
import monix.execution.CancelablePromise

import scala.annotation.implicitNotFound
import scala.concurrent.Future
import scala.util.Try

/** A lawless type class that provides conversions to [[Task]].
  *
  * Sample:
  * {{{
  *   // Conversion from cats.Eval
  *   import cats.Eval
  *
  *   val source0 = Eval.always(1 + 1)
  *   val task0 = TaskLike[Eval].toTask(source0)
  *
  *   // Conversion from Future
  *   import scala.concurrent.Future
  *
  *   val source1 = Future.successful(1 + 1)
  *   val task1 = TaskLike[Future].toTask(source1)
  *
  *   // Conversion from IO
  *   import cats.effect.IO
  *
  *   val source2 = IO(1 + 1)
  *   val task2 = TaskLike[IO].toTask(source2)
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
    * Converts from `F[A]` to `Task[A]`, preserving referential
    * transparency if `F[_]` is a pure data type and preserving
    * interruptibility if the source is cancelable.
    */
  def toTask[A](fa: F[A]): Task[A]
}

object TaskLike extends TaskLikeImplicits0 {
  /**
    * Returns the available instance for `F`.
    */
  def apply[F[_]](implicit F: TaskLike[F]): TaskLike[F] = F

  /**
    * Instance for `Task`, returning same reference.
    */
  implicit val fromTask: TaskLike[Task] =
    new TaskLike[Task] {
      def toTask[A](fa: Task[A]): Task[A] = fa
    }

  /**
    * Converts to `Task` from [[scala.concurrent.Future]].
    */
  implicit val fromFuture: TaskLike[Future] =
    new TaskLike[Future] {
      def toTask[A](fa: Future[A]): Task[A] =
        Task.fromFuture(fa)
    }

  /**
    * Converts to `Task` from [[Coeval]].
    */
  implicit val fromCoeval: TaskLike[Coeval] =
    new TaskLike[Coeval] {
      def toTask[A](fa: Coeval[A]): Task[A] =
        Task.coeval(fa)
    }

  /**
    * Converts to `Task` from `cats.effect.Eval`.
    */
  implicit val fromEval: TaskLike[Eval] =
    new TaskLike[Eval] {
      def toTask[A](fa: Eval[A]): Task[A] =
        Task.fromEval(fa)
    }

  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/datatypes/io.html cats.effect.IO]].
    */
  implicit val fromIO: TaskLike[IO] =
    new TaskLike[IO] {
      def toTask[A](fa: IO[A]): Task[A] =
        Task.fromIO(fa)
    }

  /**
    * Converts to `Task` from a `cats.effect.SyncIO`.
    */
  implicit val fromSyncIO: TaskLike[SyncIO] =
    new TaskLike[SyncIO] {
      def toTask[A](fa: SyncIO[A]): Task[A] =
        Task.fromIO(fa.toIO)
    }

  /**
    * Converts `scala.util.Try` to [[Task]].
    */
  implicit val fromTry: TaskLike[Try] =
    new TaskLike[Try] {
      def toTask[A](fa: Try[A]): Task[A] =
        Task.fromTry(fa)
    }

  /**
    * Converts [[monix.execution.CancelablePromise]] to [[Task]].
    */
  implicit val fromCancelablePromise: TaskLike[CancelablePromise] =
    new TaskLike[CancelablePromise] {
      def toTask[A](p: CancelablePromise[A]): Task[A] =
        Task.fromCancelablePromise(p)
    }

  /**
    * Converts `Function0` (parameter-less function, also called
    * thunks) to [[Task]].
    */
  implicit val fromFunction0: TaskLike[Function0] =
    new TaskLike[Function0] {
      def toTask[A](thunk: () => A): Task[A] =
        Task.Eval(thunk)
    }

  /**
    * Converts a Scala `Either` to a [[Task]].
    */
  implicit def fromEither[E <: Throwable]: TaskLike[Either[E, ?]] =
    new TaskLike[Either[E, ?]] {
      def toTask[A](fa: Either[E, A]): Task[A] =
        Task.fromEither(fa)
    }
}

private[eval] abstract class TaskLikeImplicits0 extends TaskLikeImplicits1 {
  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.ConcurrentEffect]].
    */
  implicit def fromConcurrentEffect[F[_]](implicit F: ConcurrentEffect[F]): TaskLike[F] =
    new TaskLike[F] {
      def toTask[A](fa: F[A]): Task[A] =
        Task.fromConcurrentEffect(fa)
    }
}

private[eval] abstract class TaskLikeImplicits1 extends TaskLikeImplicits2 {
  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.Async]].
    */
  implicit def fromEffect[F[_]](implicit F: Effect[F]): TaskLike[F] =
    new TaskLike[F] {
      def toTask[A](fa: F[A]): Task[A] =
        Task.fromEffect(fa)
    }
}

private[eval] abstract class TaskLikeImplicits2 {
  /**
    * Converts to `Task` from [[cats.Comonad]] values.
    */
  implicit def fromComonad[F[_]](implicit F: Comonad[F]): TaskLike[F] =
    new TaskLike[F] {
      def toTask[A](fa: F[A]): Task[A] =
        Task(F.extract(fa))
    }
}
