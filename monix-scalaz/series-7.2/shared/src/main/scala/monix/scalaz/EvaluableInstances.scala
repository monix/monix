/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.scalaz

import monix.types._
import scalaz.{Monoid, Semigroup}

/** Converts Monix's [[monix.types.Evaluable Evaluable]]
  * instances into Scalaz type-classes.
  */
trait EvaluableInstances extends EvaluableInstances1 {
  implicit def monixEvaluableToScalaz[F[_]]
    (implicit ev: Evaluable[F]): _root_.scalaz.Monad[F] with _root_.scalaz.Cobind[F] =
    new ConvertMonixEvaluableToScalaz[F] {
      override val monadRec: MonadRec[F] =
        ev.monadRec
      override val applicative: Applicative[F] =
        ev.monadRec.monad.applicative
      override val monad: Monad[F] =
        ev.monadRec.monad
      override val functor: Functor[F] =
        ev.monadRec.monad.applicative.functor
      override val coflatMap: CoflatMap[F] =
        ev.coflatMap
    }

  private[scalaz] trait ConvertMonixEvaluableToScalaz[F[_]]
    extends ConvertMonixMonadToScalaz[F]
      with ConvertMonixCoflatMapToScalaz[F]
      with ConvertMonixTailRecMonadToScalaz[F]
}

private[scalaz] trait EvaluableInstances1 extends EvaluableInstances0 {
  implicit def monixEvaluableMonoid[F[_], A](implicit F: Evaluable[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      private[this] val ap =
        F.monadRec.monad.applicative
      def zero: F[A] = ap.pure(A.zero)
      def append(f1: F[A], f2: => F[A]): F[A] =
        ap.map2(f1,f2)((a,b) => A.append(a,b))
    }
}

private[scalaz] trait EvaluableInstances0 extends ShimsInstances {
  implicit def monixEvaluableSemigroup[F[_], A](implicit F: Evaluable[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      private[this] val ap =
        F.monadRec.monad.applicative
      def append(f1: F[A], f2: => F[A]): F[A] =
        ap.map2(f1,f2)((a,b) => A.append(a,b))
    }
}