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

package monix.types.shims

/** A shim for an `MonadError` type-class, to be supplied by / translated to
  * libraries such as Cats or Scalaz.
  */
trait MonadError[F[_],E] extends ApplicativeError[F,E] with Monad[F]

object MonadError {
  @inline def apply[F[_],E](implicit F: MonadError[F,E]): MonadError[F,E] = F
}