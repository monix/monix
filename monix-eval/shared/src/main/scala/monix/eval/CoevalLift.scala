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

import cats.{ ~>, Eval }
import cats.effect._
import scala.annotation.implicitNotFound

/**
  * A lawless type class that specifies conversions from `Coeval`
  * to similar data types (i.e. pure, synchronous).
  *
  * This is nothing more than a
  * [[https://typelevel.org/cats/datatypes/functionk.html cats.arrow.FunctionK]].
  */
@implicitNotFound("""Cannot find implicit value for CoevalLift[${F}].""")
trait CoevalLift[F[_]] extends (Coeval ~> F) {
  /**
    * Converts `Coeval[A]` into `F[A]`.
    *
    * The operation should preserve laziness if possible.
    */
  def apply[A](coeval: Coeval[A]): F[A]
}

object CoevalLift extends CoevalLiftImplicits0 {
  /**
    * Returns the available [[CoevalLift]] instance for `F`.
    */
  def apply[F[_]](implicit F: CoevalLift[F]): CoevalLift[F] = F

  /**
    * Instance for converting to `Coeval`, being the identity function.
    */
  implicit val toCoeval: CoevalLift[Coeval] =
    new CoevalLift[Coeval] {
      def apply[A](coeval: Coeval[A]): Coeval[A] =
        coeval
    }

  /**
    * Instance for converting to [[Task]].
    */
  implicit val toTask: CoevalLift[Task] =
    new CoevalLift[Task] {
      def apply[A](value: Coeval[A]): Task[A] =
        Task.coeval(value)
    }

  /**
    * Instance for converting to `cats.Eval`.
    */
  implicit val toEval: CoevalLift[Eval] =
    new CoevalLift[Eval] {
      def apply[A](coeval: Coeval[A]): Eval[A] =
        coeval match {
          case Coeval.Now(value) => Eval.now(value)
          case Coeval.Error(e) => Eval.always(throw e)
          case Coeval.Always(thunk) => new cats.Always(thunk)
          case other => Eval.always(other.value())
        }
    }

  /**
    * Deprecated method, which happened on extending `FunctionK`.
    */
  implicit class Deprecated[F[_]](val inst: CoevalLift[F]) {
    /** DEPRECATED — switch to [[CoevalLift.apply]]. */
    @deprecated("Switch to CoevalLift.apply", since = "3.0.0-RC3")
    def coevalLift[A](coeval: Coeval[A]): F[A] = {
      // $COVERAGE-OFF$
      inst(coeval)
      // $COVERAGE-ON$
    }
  }
}

private[eval] abstract class CoevalLiftImplicits0 {
  /**
    * Instance for converting to any type implementing
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent.html cats.effect.Concurrent]].
    */
  implicit def toSync[F[_]](implicit F: Sync[F]): CoevalLift[F] =
    new CoevalLift[F] {
      def apply[A](coeval: Coeval[A]): F[A] =
        coeval match {
          case Coeval.Now(a) => F.pure(a)
          case Coeval.Error(e) => F.raiseError(e)
          case Coeval.Always(f) => F.delay(f())
          case _ => F.delay(coeval.value())
        }
    }
}
