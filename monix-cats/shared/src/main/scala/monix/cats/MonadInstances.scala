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

import monix.types.shims.Monad

/** Converts Monix's Monad into the Cats monad. */
trait MonadInstances {
  implicit def monixMonadInstances[F[_] : Monad]: _root_.cats.Monad[F] =
    new ConvertMonixMonadToCats[F]()

  class ConvertMonixMonadToCats[F[_]](implicit F: Monad[F]) extends _root_.cats.Monad[F] {
    def pure[A](x: A): F[A] = F.point(x)
    def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] = F.flatMap(fa)(f)
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = F.map(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] = F.flatten(ffa)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] = F.ap(fa)(ff)
  }
}
