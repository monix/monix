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

package monix.execution.internal

/** INTERNAL API — Newtype encoding
  *
  * The `Newtype1` abstract class indirection is needed for Scala 2.10,
  * otherwise we could just define these types straight on the
  * companion object. In Scala 2.10 definining these types
  * straight on the companion object yields an error like:
  * ''"only classes can have declared but undefined members"''.
  *
  * Inspired by
  * [[https://github.com/alexknvl/newtypes alexknvl/newtypes]].
  */
private[monix] abstract class Newtype1[F[_]] { self =>
  type Base
  trait Tag extends Any
  type Type[+A] <: Base with Tag

  def apply[A](fa: F[A]): Type[A] =
    fa.asInstanceOf[Type[A]]

  def unwrap[A](fa: Type[A]): F[A] =
    fa.asInstanceOf[F[A]]
}