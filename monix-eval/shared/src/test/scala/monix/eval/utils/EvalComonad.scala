/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.eval.utils

import cats.Comonad

/**
  * Custom type useful for testing `Comonad` conversions.
  */
final case class EvalComonad[A](thunk: () => A)

object EvalComonad {
  implicit val comonadInstance: Comonad[EvalComonad] =
    new Comonad[EvalComonad] {
      def extract[A](x: EvalComonad[A]): A =
        x.thunk()
      def coflatMap[A, B](fa: EvalComonad[A])(f: EvalComonad[A] => B): EvalComonad[B] =
        EvalComonad(() => f(fa))
      def map[A, B](fa: EvalComonad[A])(f: A => B): EvalComonad[B] =
        EvalComonad(() => f(fa.thunk()))
    }
}