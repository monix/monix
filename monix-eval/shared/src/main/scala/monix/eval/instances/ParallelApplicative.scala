/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval.instances

import cats.{Applicative, Parallel}

/** Given a `cats.Parallel` instance for a type `F[_]`, builds
  * a parallel `cats.Applicative[F]` out of it.
  */
private[monix] final class ParallelApplicative[F[_], G[_]]
  (implicit P: Parallel[F, G])
  extends Applicative[F] {

  override def pure[A](x: A): F[A] =
    P.monad.pure(x)
  override def unit: F[Unit] =
    P.monad.unit
  override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
    P.monad.map(fa)(f)

  override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    P.sequential(P.applicative.ap(P.parallel(ff))(P.parallel(fa)))
  override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    P.sequential(P.applicative.product(P.parallel(fa), P.parallel(fb)))
  override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    P.sequential(P.applicative.map2(P.parallel(fa), P.parallel(fb))(f))
}

private[monix] object ParallelApplicative {
  /** Given a `cats.Parallel` instance, builds a parallel `cats.Applicative`
    * out of it.
    */
  def apply[F[_], G[_]](implicit P: Parallel[F, G]): Applicative[F] =
    P match {
      case CatsParallelForTask =>
        P.applicative
      case _ =>
        new ParallelApplicative[F, G]()
    }
}