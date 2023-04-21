/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.tail

import cats.syntax.all._
import cats.effect.Sync
import monix.tail.Iterant.{ Concat, Scope }

package object internal {
  /**
    * Internal API — extension methods used in the implementation.
    */
  private[tail] implicit class ScopeExtensions[F[_], S, A](val self: Scope[F, S, A]) extends AnyVal {

    def runMap[B](f: Iterant[F, A] => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] =
      self.copy(use = AndThen(self.use).andThen(F.map(_)(f)))

    def runFlatMap[B](f: Iterant[F, A] => F[Iterant[F, B]])(implicit F: Sync[F]): F[Iterant[F, B]] =
      F.pure(self.copy(use = AndThen(self.use).andThen(F.flatMap(_)(f))))

    def runFold[B](f: Iterant[F, A] => F[B])(implicit F: Sync[F]): F[B] =
      F.bracketCase(self.acquire)(AndThen(self.use).andThen(_.flatMap(f)))(self.release)
  }

  /**
    * Internal API — extension methods used in the implementation.
    */
  private[tail] implicit class ConcatExtensions[F[_], A](val self: Concat[F, A]) extends AnyVal {

    def runMap[B](f: Iterant[F, A] => Iterant[F, B])(implicit F: Sync[F]): Concat[F, B] =
      Concat(self.lh.map(f), self.rh.map(f))
  }
}
