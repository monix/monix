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

import monix.types.shims._

/** Groups all shim instances together. */
trait ShimsInstances extends BimonadInstances

/** Converts Monix's [[Bimonad]] into the Cats Bimonad. */
trait BimonadInstances extends MonadInstances with ComonadInstances {
  implicit def monixBimonadInstancesToCats[F[_] : Bimonad]: _root_.cats.Bimonad[F] =
    new ConvertMonixBimonadToCats[F]()

  class ConvertMonixBimonadToCats[F[_]](implicit F: Bimonad[F])
    extends ConvertMonixMonadToCats[F] with _root_.cats.Bimonad[F] {

    override def extract[A](x: F[A]): A = F.extract(x)
    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] = F.coflatten(fa)
  }
}

/** Converts Monix's [[Comonad]] into the Cats Comonad. */
trait ComonadInstances extends CoflatMapInstances {
  implicit def monixComonadInstancesToCats[F[_] : Comonad]: _root_.cats.Comonad[F] =
    new ConvertMonixComonadToCats[F]()

  class ConvertMonixComonadToCats[F[_]](implicit F: Comonad[F])
    extends ConvertMonixCoflatMapToCats[F] with _root_.cats.Comonad[F] {

    override def extract[A](x: F[A]): A = F.extract(x)
  }
}

/** Converts Monix's [[CoflatMap]] into the Cats CoflatMap. */
trait CoflatMapInstances extends FunctorInstances {
  implicit def monixCoflatMapInstancesToCats[F[_] : CoflatMap]: _root_.cats.CoflatMap[F] =
    new ConvertMonixCoflatMapToCats[F]()

  class ConvertMonixCoflatMapToCats[F[_]](implicit F: CoflatMap[F])
    extends ConvertMonixFunctorToCats[F] with _root_.cats.CoflatMap[F] {

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] = F.coflatten(fa)
  }
}

/** Converts Monix's Monad into the Cats monad. */
trait MonadInstances extends ApplicativeInstances {
  implicit def monixMonadInstancesToCats[F[_] : Monad]: _root_.cats.Monad[F] =
    new ConvertMonixMonadToCats[F]()

  class ConvertMonixMonadToCats[F[_]](implicit F: Monad[F])
    extends ConvertMonixApplicativeToCats[F] with _root_.cats.Monad[F] {

    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] = F.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] = F.flatten(ffa)
  }
}

/** Converts Monix's Applicative into the Cats Applicative. */
trait ApplicativeInstances extends FunctorInstances {
  implicit def monixApplicativeInstancesToCats[F[_] : Applicative]: _root_.cats.Applicative[F] =
    new ConvertMonixApplicativeToCats[F]()

  class ConvertMonixApplicativeToCats[F[_]](implicit F: Applicative[F])
    extends ConvertMonixFunctorToCats[F] with _root_.cats.Applicative[F] {

    override def pure[A](x: A): F[A] = F.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] = F.ap(fa)(ff)
  }
}

/** Converts Monix's [[Functor]] into the Cats Functor. */
trait FunctorInstances {
  implicit def monixFunctorInstancesToCats[F[_] : Functor]: _root_.cats.Functor[F] =
    new ConvertMonixFunctorToCats[F]()

  class ConvertMonixFunctorToCats[F[_]](implicit F: Functor[F]) extends _root_.cats.Functor[F] {
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = F.map(fa)(f)
  }
}