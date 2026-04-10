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

package monix.reactive

import cats.{ ~>, Eval }
import cats.effect.{ IO, SyncIO }
import monix.eval.{ Coeval, Task, TaskLike }
import monix.reactive.internal.builders.EvalAlwaysObservable
import org.reactivestreams.{ Publisher => RPublisher }

import scala.annotation.implicitNotFound
import scala.concurrent.Future
import scala.util.Try

/** A lawless type class that provides conversions to [[Observable]].
  *
  * Sample:
  * {{{
  *   // Conversion from cats.Eval
  *   import cats.Eval
  *
  *   val source0 = Eval.always(1 + 1)
  *   val task0 = ObservableLike[Eval].apply(source0)
  *
  *   // Conversion from Future
  *   import scala.concurrent.Future
  *
  *   val source1 = Future.successful(1 + 1)
  *   val task1 = ObservableLike[Future].apply(source1)
  *
  *   // Conversion from IO
  *   import cats.effect.IO
  *
  *   val source2 = IO(1 + 1)
  *   val task2 = ObservableLike[IO].apply(source2)
  * }}}
  *
  * See [[Observable.from]]
  */
@implicitNotFound("""Cannot find implicit value for ObservableLike[${F}].
Building this implicit value might depend on having an implicit
s.c.ExecutionContext in scope, a Scheduler or some equivalent type.""")
trait ObservableLike[F[_]] extends (F ~> Observable) {
  /**
    * Converts from `F[A]` to `Observable[A]`, preserving referential
    * transparency if `F[_]` is a pure data type and preserving
    * interruptibility if the source is cancelable.
    */
  def apply[A](fa: F[A]): Observable[A]
}

object ObservableLike extends ObservableLikeImplicits0 {
  /**
    * Returns the available instance for `F`.
    */
  def apply[F[_]](implicit F: ObservableLike[F]): ObservableLike[F] = F

  /**
    * Instance for `Observable`, returning same reference.
    */
  implicit val fromObservable: ObservableLike[Observable] =
    new ObservableLike[Observable] {
      def apply[A](fa: Observable[A]): Observable[A] = fa
    }

  /**
    * Converts to `Observable` from [[monix.eval.Task]].
    */
  implicit val fromTask: ObservableLike[Task] =
    new ObservableLike[Task] {
      def apply[A](fa: Task[A]): Observable[A] =
        Observable.fromTask(fa)
    }

  /**
    * Converts to `Observable` from [[scala.concurrent.Future]].
    */
  implicit val fromFuture: ObservableLike[Future] =
    new ObservableLike[Future] {
      def apply[A](fa: Future[A]): Observable[A] =
        Observable.fromFuture(fa)
    }

  /**
    * Converts to `Observable` from [[monix.eval.Coeval]].
    */
  implicit val fromCoeval: ObservableLike[Coeval] =
    new ObservableLike[Coeval] {
      def apply[A](fa: Coeval[A]): Observable[A] =
        Observable.coeval(fa)
    }

  /**
    * Converts to `Observable` from `cats.effect.Eval`.
    */
  implicit val fromEval: ObservableLike[Eval] =
    new ObservableLike[Eval] {
      def apply[A](fa: Eval[A]): Observable[A] =
        fa match {
          case cats.Now(v) => Observable.now(v)
          case _ => Observable.eval(fa.value)
        }
    }

  /**
    * Converts to `Observable` from
    * [[https://typelevel.org/cats-effect/datatypes/io.html cats.effect.IO]].
    */
  implicit val fromIO: ObservableLike[IO] =
    new ObservableLike[IO] {
      def apply[A](fa: IO[A]): Observable[A] =
        Observable.fromTask(Task.from(fa))
    }

  /**
    * Converts to `Observable` from a `cats.effect.SyncIO`.
    */
  implicit val fromSyncIO: ObservableLike[SyncIO] =
    new ObservableLike[SyncIO] {
      def apply[A](fa: SyncIO[A]): Observable[A] =
        Observable.from(fa.toIO)
    }

  /**
    * Converts a `scala.util.Try` to a [[Observable]].
    */
  implicit val fromTry: ObservableLike[Try] =
    new ObservableLike[Try] {
      def apply[A](fa: Try[A]): Observable[A] =
        Observable.fromTry(fa)
    }

  /**
    * Converts `Function0` (parameter-less function, also called
    * thunks) to [[Observable]].
    */
  implicit val fromFunction0: ObservableLike[Function0] =
    new ObservableLike[Function0] {
      def apply[A](thunk: () => A): Observable[A] =
        new EvalAlwaysObservable(thunk)
    }

  /**
    * Converts a Scala `Either` to a [[Observable]].
    */
  implicit def fromEither[E <: Throwable]: ObservableLike[Either[E, *]] =
    new ObservableLike[Either[E, *]] {
      def apply[A](fa: Either[E, A]): Observable[A] =
        Observable.fromEither(fa)
    }

  /**
    * Converts a Scala `Iterator` to a [[Observable]].
    */
  implicit def fromIterable[F[X] <: Iterable[X]]: ObservableLike[F] =
    new ObservableLike[F] {
      def apply[A](fa: F[A]): Observable[A] =
        Observable.fromIterable(fa)
    }

  /**
    * Deprecated method, which happened on extending `FunctionK`.
    */
  implicit class Deprecated[F[_]](val inst: ObservableLike[F]) {
    /** DEPRECATED — switch to [[ObservableLike.apply]]. */
    @deprecated("Switch to ObservableLike.apply", since = "3.0.0-RC3")
    def toObservable[A](observable: F[A]): Observable[A] = {
      // $COVERAGE-OFF$
      inst(observable)
      // $COVERAGE-ON$
    }
  }
}

private[reactive] abstract class ObservableLikeImplicits0 {
  /**
    * Converts to `Observable` from
    * [[https://reactivestreams.org/ org.reactivestreams.Publisher]].
    */
  implicit val fromReactivePublisher: ObservableLike[RPublisher] =
    new ObservableLike[RPublisher] {
      def apply[A](fa: RPublisher[A]): Observable[A] =
        Observable.fromReactivePublisher(fa)
    }

  /**
    * Converts to `Observable` from
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html cats.effect.ConcurrentEffect]].
    */
  implicit def fromTaskLike[F[_]](implicit F: TaskLike[F]): ObservableLike[F] =
    new ObservableLike[F] {
      def apply[A](fa: F[A]): Observable[A] =
        Observable.fromTaskLike(fa)
    }
}
