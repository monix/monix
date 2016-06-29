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

import monix.types.Evaluable
import scalaz.{Cobind, MonadError, Monoid, Semigroup}

/** Converts Monix's [[monix.types.Evaluable Evaluable]]
  * instances into Scalaz type-classes.
  */
trait EvaluableInstances extends EvaluableInstances1 {
  implicit def monixEvaluableToScalaz[F[_]]
    (implicit ev: Evaluable[F]): MonadError[F,Throwable] with Cobind[F] =
    new ConvertMonixEvaluableToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixEvaluableToScalaz[F[_]]
    extends ConvertMonixMonadErrorToScalaz[F,Throwable] with ConvertMonixCoflatMapToScalaz[F] {

    override val F: Evaluable[F]
  }
}

private[scalaz] trait EvaluableInstances1 extends EvaluableInstances0 {
  implicit def monixEvaluableMonoid[F[_], A](implicit F: Evaluable[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      def zero: F[A] = F.pure(A.zero)
      def append(f1: F[A], f2: => F[A]): F[A] =
        F.map2(f1,f2)((a,b) => A.append(a,b))
    }
}

private[scalaz] trait EvaluableInstances0 extends ShimsInstances {
  implicit def monixEvaluableSemigroup[F[_], A](implicit F: Evaluable[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      def append(f1: F[A], f2: => F[A]): F[A] =
        F.map2(f1,f2)((a,b) => A.append(a,b))
    }
}