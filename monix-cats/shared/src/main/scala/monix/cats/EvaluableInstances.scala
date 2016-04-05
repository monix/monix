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
import cats.{CoflatMap, Eval, MonadError}
import monix.eval.Evaluable
import language.higherKinds

trait EvaluableInstances extends EvaluableInstances2 {
  import Evaluable.ops._

  implicit def evaluableInstances[F[_]](implicit F : Evaluable[F]): MonadError[F, Throwable] with CoflatMap[F] =
    new MonadError[F, Throwable] with CoflatMap[F] {
      def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
        F.evalAlways(f(fa))
      def handleErrorWith[A](fa: F[A])(f: (Throwable) => F[A]): F[A] =
        fa.onErrorHandleWith(f)
      def raiseError[A](e: Throwable): F[A] =
        F.error(e)
      def pure[A](x: A): F[A] =
        F.now(x)

      override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
        fa.map(f)
      override def handleError[A](fa: F[A])(f: (Throwable) => A): F[A] =
        fa.onErrorHandle(f)
      override def pureEval[A](x: Eval[A]): F[A] =
        F.evalAlways(x.value)
      override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
        F.zipWith2(fa,fb)(f)
    }
}

private[cats] trait EvaluableInstances2 extends EvaluableInstances1 {
  implicit def taskGroup[A,F[_]](implicit A: Group[A], F: Evaluable[F]): Group[F[A]] =
    new Group[F[A]] {
      val empty: F[A] = F.now(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.zipWith2(x,y)(A.combine)
      def inverse(a: F[A]): F[A] =
        F.map(a)(A.inverse)
    }
}

private[cats] trait EvaluableInstances1 extends EvaluableInstances0 {
  implicit def taskMonoid[A,F[_]](implicit A: Monoid[A], F: Evaluable[F]): Monoid[F[A]] =
    new Monoid[F[A]] {
      val empty: F[A] = F.now(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.zipWith2(x,y)(A.combine)
    }
}

private[cats] trait EvaluableInstances0 {
  implicit def taskSemigroup[A,F[_]](implicit A: Semigroup[A], F: Evaluable[F]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      def combine(x: F[A], y: F[A]): F[A] =
        F.zipWith2(x,y)(A.combine)
    }
}
