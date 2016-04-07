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

import algebra.{Group, Monoid, Semigroup}
import cats.{Bimonad, MonadError}
import monix.eval.Coeval

/** Provides Cats compatibility for the [[monix.eval.Coeval]] type. */
trait CoevalInstances extends CoevalInstances2 {
  implicit val coevalInstances: MonadError[Coeval, Throwable] with Bimonad[Coeval] =
    new ConvertMonixDeferrableToCats[Coeval]()(Coeval.instances) with Bimonad[Coeval] {
      def extract[A](x: Coeval[A]): A = x.value
    }
}

private[cats] trait CoevalInstances2 extends CoevalInstances1 {
  implicit def coevalGroup[A](implicit A: Group[A]): Group[Coeval[A]] =
    new Group[Coeval[A]] {
      val empty: Coeval[A] = Coeval.now(A.empty)
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipWith(y)(A.combine)
      def inverse(a: Coeval[A]): Coeval[A] =
        a.map(A.inverse)
    }
}

private[cats] trait CoevalInstances1 extends CoevalInstances0 {
  implicit def coevalMonoid[A](implicit A: Monoid[A]): Monoid[Coeval[A]] =
    new Monoid[Coeval[A]] {
      val empty: Coeval[A] = Coeval.now(A.empty)
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipWith(y)(A.combine)
    }
}

private[cats] trait CoevalInstances0 extends DeferrableInstances {
  implicit def coevalSemigroup[A](implicit A: Semigroup[A]): Semigroup[Coeval[A]] =
    new Semigroup[Coeval[A]] {
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipWith(y)(A.combine)
    }
}