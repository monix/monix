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

import cats.effect._
import cats.{ ~>, Eval }
import monix.catnap.FutureLift
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
  *   val task0 = TaskLike[Eval].apply(source0)
  *
  *   // Conversion from Future
  *   import scala.concurrent.Future
  *
  *   val source1 = Future.successful(1 + 1)
  *   val task1 = TaskLike[Future].apply(source1)
  *
  *   // Conversion from IO
  *   import cats.effect.IO
  *
  *   val source2 = IO(1 + 1)
  *   val task2 = TaskLike[IO].apply(source2)
  * }}}
  *
  * This is an alternative to usage of `cats.effect.Effect`
  * where the internals are specialized to `Task` anyway, like for
  * example the implementation of `monix.reactive.Observable`.
  */
@implicitNotFound("""Cannot find implicit value for TaskLike[${F}].
Building this implicit value might depend on having an implicit
s.c.ExecutionContext in scope, a Scheduler or some equivalent type.""")
trait TaskLike[F[_]] extends (F ~> Task) {
  /**
    * Converts from `F[A]` to `Task[A]`, preserving referential
    * transparency if `F[_]` is a pure data type and preserving
    * interruptibility if the source is cancelable.
    */
  def apply[A](fa: F[A]): Task[A]
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
      def apply[A](fa: Task[A]): Task[A] = fa
    }

  /**
    * Converts to `Task` from [[scala.concurrent.Future]].
    */
  implicit val fromFuture: TaskLike[Future] =
    new TaskLike[Future] {
      def apply[A](fa: Future[A]): Task[A] =
        Task.fromFuture(fa)
    }

  /**
    * Converts to `Task` from [[Coeval]].
    */
  implicit val fromCoeval: TaskLike[Coeval] =
    new TaskLike[Coeval] {
      def apply[A](fa: Coeval[A]): Task[A] =
        Task.coeval(fa)
    }

  /**
    * Converts to `Task` from `cats.effect.Eval`.
    */
  implicit val fromEval: TaskLike[Eval] =
    new TaskLike[Eval] {
      def apply[A](fa: Eval[A]): Task[A] =
        Coeval.from(fa).to[Task]
    }

  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/datatypes/io.html cats.effect.IO]].
    */
  implicit val fromIO: TaskLike[IO] =
    new TaskLike[IO] {
      def apply[A](fa: IO[A]): Task[A] =
        Concurrent.liftIO[Task, A](fa)
    }

  /**
    * Converts to `Task` from a `cats.effect.SyncIO`.
    */
  implicit val fromSyncIO: TaskLike[SyncIO] =
    new TaskLike[SyncIO] {
      def apply[A](fa: SyncIO[A]): Task[A] =
        Concurrent.liftIO[Task, A](fa.toIO)
    }

  /**
    * Converts `scala.util.Try` to [[Task]].
    */
  implicit val fromTry: TaskLike[Try] =
    new TaskLike[Try] {
      def apply[A](fa: Try[A]): Task[A] =
        Task.fromTry(fa)
    }

  /**
    * Converts [[monix.execution.CancelablePromise]] to [[Task]].
    */
  implicit val fromCancelablePromise: TaskLike[CancelablePromise] =
    new TaskLike[CancelablePromise] {
      def apply[A](p: CancelablePromise[A]): Task[A] =
        Task.fromCancelablePromise(p)
    }

  /**
    * Converts `Function0` (parameter-less function, also called
    * thunks) to [[Task]].
    */
  implicit val fromFunction0: TaskLike[Function0] =
    new TaskLike[Function0] {
      def apply[A](thunk: () => A): Task[A] =
        Task.Eval(thunk)
    }

  /**
    * Converts a Scala `Either` to a [[Task]].
    */
  implicit def fromEither[E <: Throwable]: TaskLike[Either[E, *]] =
    new TaskLike[Either[E, *]] {
      def apply[A](fa: Either[E, A]): Task[A] =
        Task.fromEither(fa)
    }

  /**
    * Deprecated method, which happened on extending `FunctionK`.
    */
  implicit class Deprecated[F[_]](val inst: TaskLike[F]) {
    /** DEPRECATED — switch to [[TaskLike.apply]]. */
    @deprecated("Switch to TaskLike.apply", since = "3.0.0-RC3")
    def toTask[A](task: F[A]): Task[A] = {
      // $COVERAGE-OFF$
      inst(task)
      // $COVERAGE-ON$
    }
  }
}

private[eval] abstract class TaskLikeImplicits0 extends TaskLikeImplicits1 {
  /**
    * Converts to `Task` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.ConcurrentEffect]].
    */
  implicit def fromConcurrentEffect[F[_]](implicit F: ConcurrentEffect[F]): TaskLike[F] =
    new TaskLike[F] {
      def apply[A](fa: F[A]): Task[A] =
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
      def apply[A](fa: F[A]): Task[A] =
        Task.fromEffect(fa)
    }
}

private[eval] abstract class TaskLikeImplicits2 {
  /**
    * Converts from any `Future`-like type, via [[monix.catnap.FutureLift]].
    */
  implicit def fromAnyFutureViaLift[F[_]](implicit F: FutureLift[Task, F]): TaskLike[F] =
    new TaskLike[F] {
      def apply[A](fa: F[A]): Task[A] =
        Task.fromFutureLike(Task.now(fa))
    }
}
