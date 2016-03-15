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

package monix

import scala.language.higherKinds

package object types {
  /** Alias for the Cats `Functor` type.
    *
    * The name is short for "covariant functor".
    */
  type Functor[F[_]] = _root_.cats.Functor[F]
  /** Alias for the Cats `Functor` companion. */
  val Functor = _root_.cats.Functor

  /** Alias for the Cats `Monad` type.
    *
    * Allows composition of dependent effectful functions.
    */
  type Monad[F[_]] = _root_.cats.Monad[F]
  /** Alias for the Cats `Monad` companion. */
  val Monad = _root_.cats.Monad

  /** Alias for the Cats `MonadError` type.
    *
    * It's a [[Monad]] that also allows you to raise and or handle an error value.
    */
  type MonadError[F[_],E] = _root_.cats.MonadError[F,E]
  /** Alias for the Cats `MonadError` companion. */
  val MonadError = _root_.cats.MonadError

  /** Alias for the Cats `MonadFilter` type.
    *
    * It's a [[Monad]] that can be empty and filterable.
    */
  type MonadFilter[F[_]] = _root_.cats.MonadFilter[F]
  /** Alias for the Cats `MonadFilter` companion. */
  val MonadFilter = _root_.cats.MonadFilter
}
