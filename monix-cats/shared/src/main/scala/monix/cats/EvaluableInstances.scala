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

import cats.{CoflatMap, Group, MonadError, Monoid, Semigroup}
import monix.types.Evaluable

/** Converts Monix's [[monix.types.Evaluable Evaluable]]
  * instances into Cats type-classes.
  */
trait EvaluableInstances extends EvaluableInstances2 {
  implicit def monixEvaluableToCats[F[_]]
    (implicit ev: Evaluable[F]): MonadError[F,Throwable] with CoflatMap[F] =
    new ConvertMonixEvaluableToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixEvaluableToCats[F[_]]
    extends ConvertMonixMonadErrorToCats[F,Throwable] with ConvertMonixCoflatMapToCats[F] {

    override val F: Evaluable[F]
  }
}

private[cats] trait EvaluableInstances2 extends EvaluableInstances1 {
  implicit def monixEvaluableGroup[F[_], A](implicit F: Evaluable[F], A: Group[A]): Group[F[A]] =
    new Group[F[A]] {
      def empty: F[A] = F.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.flatMap(x)(a => F.map(y)(b => A.combine(a,b)))
      def inverse(a: F[A]): F[A] =
        F.map(a)(A.inverse)
    }
}

private[cats] trait EvaluableInstances1 extends EvaluableInstances0 {
  implicit def monixEvaluableMonoid[F[_], A](implicit F: Evaluable[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      def empty: F[A] = F.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.map2(x,y)(A.combine)
    }
}

private[cats] trait EvaluableInstances0 extends ShimsInstances {
  implicit def monixEvaluableSemigroup[F[_], A](implicit F: Evaluable[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      def combine(x: F[A], y: F[A]): F[A] =
        F.map2(x,y)(A.combine)
    }
}