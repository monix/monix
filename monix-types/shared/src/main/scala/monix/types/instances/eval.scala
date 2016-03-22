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

package monix.types.instances

import cats.Eval
import monix.types.Nonstrict

trait EvalInstances {
  implicit val nonstrictEval: Nonstrict[Eval] =
    new Nonstrict[Eval] {
      def value[A](fa: Eval[A]): A = fa.value
      def now[A](a: A): Eval[A] = Eval.now(a)
      def flatMap[A, B](fa: Eval[A])(f: (A) => Eval[B]): Eval[B] = fa.flatMap(f)

      override def evalAlways[A](a: => A): Eval[A] = Eval.always(a)
      override def evalOnce[A](a: => A): Eval[A] = Eval.later(a)
      override def pureEval[A](x: Eval[A]): Eval[A] = x
      override def memoize[A](fa: Eval[A]): Eval[A] = fa.memoize
    }
}

object eval extends EvalInstances
