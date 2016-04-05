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
import cats.{Bimonad, Eval, MonadError}
import monix.eval.Coeval

/** Provides Cats compatibility for the [[Coeval]] type. */
trait CoevalInstances extends CoevalInstances2 {
  implicit val coevalInstances: Evaluable[Coeval] =
    new MonadError[Coeval, Throwable] with Bimonad[Coeval] {
      def extract[A](x: Coeval[A]): A = x.value
      def flatMap[A, B](fa: Coeval[A])(f: (A) => Coeval[B]): Coeval[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: Coeval[A])(f: (Coeval[A]) => B): Coeval[B] =
        Coeval.evalAlways(f(fa))
      def handleErrorWith[A](fa: Coeval[A])(f: (Throwable) => Coeval[A]): Coeval[A] =
        fa.onErrorHandleWith(f)
      def raiseError[A](e: Throwable): Coeval[A] =
        Coeval.error(e)
      def pure[A](x: A): Coeval[A] =
        Coeval.now(x)
      override def map[A, B](fa: Coeval[A])(f: (A) => B): Coeval[B] =
        fa.map(f)
      override def handleError[A](fa: Coeval[A])(f: (Throwable) => A): Coeval[A] =
        fa.onErrorHandle(f)
      override def pureEval[A](x: Eval[A]): Coeval[A] =
        Coeval.evalAlways(x.value)
      override def map2[A, B, Z](fa: Coeval[A], fb: Coeval[B])(f: (A, B) => Z): Coeval[Z] =
        fa.zipWith(fb)(f)
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

private[cats] trait CoevalInstances0 {
  implicit def coevalSemigroup[A](implicit A: Semigroup[A]): Semigroup[Coeval[A]] =
    new Semigroup[Coeval[A]] {
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipWith(y)(A.combine)
    }
}