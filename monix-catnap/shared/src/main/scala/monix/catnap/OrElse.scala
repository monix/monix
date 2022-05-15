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

package monix.catnap

/** A type class for prioritized implicit search.
  *
  * Useful for specifying type class instance alternatives.
  * Examples:
  *
  *  - `Async[F] OrElse Sync[F]`
  *  - `Concurrent[F] OrElse Async[F]`
  *
  * Inspired by the implementations in Shapeless and Algebra.
  */
sealed trait OrElse[+A, +B] {
  def fold[C](prim: A => C, sec: B => C): C
  def unify[C >: B](implicit ev: A <:< C): C
}

object OrElse extends OrElse0 {
  implicit def primary[A, B](implicit a: A): A OrElse B =
    new Primary(a)
}

private[catnap] abstract class OrElse0 {
  implicit def secondary[A, B](implicit b: B): A OrElse B =
    new Secondary(b)

  final class Primary[+A](value: A) extends OrElse[A, Nothing] {
    def fold[C](prim: A => C, sec: Nothing => C) = prim(value)
    def unify[C >: Nothing](implicit ev: <:<[A, C]): C = value
  }

  final class Secondary[+B](value: B) extends OrElse[Nothing, B] {
    def fold[C](prim: Nothing => C, sec: B => C) = sec(value)
    def unify[C >: B](implicit ev: <:<[Nothing, C]): C = value
  }
}
