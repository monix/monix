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

import cats.Eval
import simulacrum._
import scala.language.{higherKinds, implicitConversions}

/** Type-class for monadic data-structures that can expose
  * multiple `A` elements.
  *
  * Instances of this type have a `cons` operation for building
  * sequences of elements by appending a lazy `tail` to a `head`.
  *
  * The `flatMap` operation is henceforth known as `concatMap`
  * because in effect it will produce concatenation and it's good
  * to precisely differentiate from other non-deterministic ways
  * of merging sequences and that might cause confusion.
  */
@typeclass trait MonadCons[F[_]] extends Monad[F] {
  /** Builds an instance by joining a head and a lazy tail. */
  def cons[A](head: A, tail: Eval[F[A]]): F[A]

  /** Alias for `flatMap`. */
  def concatMap[A,B](fa: F[A])(f: A => F[B]): F[B]

  /** Alias for `flatten`. */
  def concat[A](ffa: F[F[A]]): F[A] =
    concatMap(ffa)(fa => fa)

  /** Concatenates the source with `other`. */
  @op("++") def followWith[A](fa: F[A], other: => F[A]): F[A] =
    concat(cons(fa, Eval.always(pure(other))))

  /** Appends the given `elem` at the end. */
  @op(":+") def endWithElem[A](fa: F[A])(elem: A): F[A] =
    followWith(fa, pure(elem))

  /** Prepends the given `elem` at the start. */
  @op("+:") def startWithElem[A](fa: F[A])(elem: A): F[A] =
    followWith(pure(elem), fa)

  /** Repeats the source, continuously. */
  def repeat[A](fa: F[A]): F[A] =
    followWith(fa, repeat(fa))

  final override def flatten[A](ffa: F[F[A]]): F[A] =
    concat(ffa)

  final override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
    concatMap(fa)(f)
}
