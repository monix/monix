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

import cats.effect.SyncIO
import cats.{ ~>, Eval }
import scala.util.Try

/** A lawless type class that provides conversions to [[Coeval]].
  *
  * Sample:
  * {{{
  *   // Conversion from cats.Eval
  *   import cats.Eval
  *
  *   val source0 = Eval.always(1 + 1)
  *   val task0 = CoevalLike[Eval].apply(source0)
  *
  *   // Conversion from SyncIO
  *   import cats.effect.SyncIO
  *
  *   val source1 = SyncIO(1 + 1)
  *   val task1 = CoevalLike[SyncIO].apply(source1)
  * }}}
  *
  * This is an alternative to usage of `cats.effect.Effect`
  * where the internals are specialized to `Coeval` anyway, like for
  * example the implementation of `monix.reactive.Observable`.
  */
trait CoevalLike[F[_]] extends (F ~> Coeval) {
  /**
    * Converts from `F[A]` to `Coeval[A]`, preserving referential
    * transparency if `F[_]` is a pure data type and preserving
    * interruptibility if the source is cancelable.
    */
  def apply[A](fa: F[A]): Coeval[A]
}

object CoevalLike {
  /**
    * Returns the available instance for `F`.
    */
  def apply[F[_]](implicit F: CoevalLike[F]): CoevalLike[F] = F

  /**
    * Instance for `Coeval`, returning same reference.
    */
  implicit val fromCoeval: CoevalLike[Coeval] =
    new CoevalLike[Coeval] {
      def apply[A](fa: Coeval[A]): Coeval[A] = fa
    }

  /**
    * Converts a `cats.Eval` to a [[Coeval]].
    */
  implicit val fromEval: CoevalLike[Eval] =
    new CoevalLike[Eval] {
      def apply[A](fa: Eval[A]): Coeval[A] =
        fa match {
          case cats.Now(v) => Coeval.Now(v)
          case other => Coeval.eval(other.value)
        }
    }

  /**
    * Converts a `cats.effect.SyncIO` to a [[Coeval]].
    */
  implicit val fromSyncIO: CoevalLike[SyncIO] =
    new CoevalLike[SyncIO] {
      def apply[A](fa: SyncIO[A]): Coeval[A] =
        Coeval(fa.unsafeRunSync())
    }

  /**
    * Converts a `scala.util.Try` to a [[Coeval]].
    */
  implicit val fromTry: CoevalLike[Try] =
    new CoevalLike[Try] {
      def apply[A](fa: Try[A]): Coeval[A] =
        Coeval.fromTry(fa)
    }

  /**
    * Converts `Function0` (parameter-less function, also called
    * thunks) to [[Coeval]].
    */
  implicit val fromFunction0: CoevalLike[Function0] =
    new CoevalLike[Function0] {
      def apply[A](thunk: () => A): Coeval[A] =
        Coeval.Always(thunk)
    }

  /**
    * Converts a Scala `Either` to a [[Coeval]].
    */
  implicit def fromEither[E <: Throwable]: CoevalLike[Either[E, *]] =
    new CoevalLike[Either[E, *]] {
      def apply[A](fa: Either[E, A]): Coeval[A] =
        Coeval.fromEither(fa)
    }

  /**
    * Deprecated method, which happened on extending `FunctionK`.
    */
  implicit class Deprecated[F[_]](val inst: CoevalLike[F]) {
    /** DEPRECATED — switch to [[CoevalLike.apply]]. */
    @deprecated("Switch to CoevalLike.apply", since = "3.0.0-RC3")
    def toCoeval[A](coeval: F[A]): Coeval[A] = {
      // $COVERAGE-OFF$
      inst(coeval)
      // $COVERAGE-ON$
    }
  }
}
