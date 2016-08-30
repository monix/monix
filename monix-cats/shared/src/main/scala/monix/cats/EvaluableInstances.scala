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

package monix.cats

import cats.{Group, Monoid, Semigroup}
import monix.types._

/** Converts Monix's [[monix.types.Evaluable Evaluable]]
  * instances into Cats type-classes.
  */
trait EvaluableInstances extends EvaluableInstances2 {
  implicit def monixEvaluableToCats[F[_]]
    (implicit ev: Evaluable[F]): _root_.cats.Monad[F] with _root_.cats.CoflatMap[F] =
    new ConvertMonixEvaluableToCats[F] {
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

  private[cats] trait ConvertMonixEvaluableToCats[F[_]]
    extends ConvertMonixCoflatMapToCats[F]
      with ConvertMonixMonadRecToCats[F]
}

private[cats] trait EvaluableInstances2 extends EvaluableInstances1 {
  implicit def monixEvaluableGroup[F[_], A](implicit F: Evaluable[F], A: Group[A]): Group[F[A]] =
    new Group[F[A]] {
      private[this] val ap =
        F.monadRec.monad.applicative
      def empty: F[A] =
        ap.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        ap.map2(x,y)(A.combine)
      def inverse(a: F[A]): F[A] =
        ap.functor.map(a)(A.inverse)
    }
}

private[cats] trait EvaluableInstances1 extends EvaluableInstances0 {
  implicit def monixEvaluableMonoid[F[_], A]
    (implicit F: Evaluable[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      private[this] val ap =
        F.monadRec.monad.applicative
      def empty: F[A] =
        ap.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        ap.map2(x,y)(A.combine)
    }
}

private[cats] trait EvaluableInstances0 extends ShimsInstances {
  implicit def monixEvaluableSemigroup[F[_], A]
    (implicit F: Evaluable[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      private[this] val ap =
        F.monadRec.monad.applicative
      def combine(x: F[A], y: F[A]): F[A] =
        ap.map2(x,y)(A.combine)
    }
}