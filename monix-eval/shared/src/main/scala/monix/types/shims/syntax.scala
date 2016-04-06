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

object syntax {
  type \/[+A, +B] = Either[A, B]

  object \/- { // scalastyle:off
    def apply[A, B](b: B): A \/ B = Right(b)
    def unapply[A, B](either: Either[A, B]): Option[B] = either.right.toOption
  }

  object -\/ {
    def apply[A, B](a: A): A \/ B = Left(a)
    def unapply[A, B](either: Either[A, B]): Option[A] = either.left.toOption
  }

  implicit class EitherSyntax[A, B](val either: Either[A, B]) extends AnyVal {
    def flatMap[C](f: B => Either[A, C]): Either[A, C] = either.right flatMap f
    def flatten[C](implicit ev: B =:= Either[A, C]): Either[A, C] = flatMap(ev)
    def map[C](f: B => C): Either[A, C] = either.right map f
  }
}
