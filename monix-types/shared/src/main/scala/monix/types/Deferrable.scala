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

package monix.types

/** Type-class describing an [[Applicative]] which supports
  * capturing a deferred evaluation of a by-name `F[A]`.
  *
  * Evaluation can be suspended until a value is extracted.
  * The `suspend` operation can be thought of as a factory
  * of `F[A]` instances, that will produce fresh instances,
  * along with possible side-effects, on each evaluation.
  */
trait Deferrable[F[_]] extends Applicative[F] {
  def defer[A](fa: => F[A]): F[A]

  def eval[A](a: => A): F[A] =
    defer(pure(a))
}

object Deferrable {
  @inline def apply[F[_]](implicit F: Deferrable[F]): Deferrable[F] = F
}
