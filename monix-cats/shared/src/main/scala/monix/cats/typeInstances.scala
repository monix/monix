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

package monix.cats

import cats.Eval
import monix.types._

object typeInstances extends TypeInstances

trait TypeInstances {
  /** Monix type-class instances for `cats.Eval`. */
  implicit val monixCatsEvalInstances: MonixCatsEvalInstances =
    new MonixCatsEvalInstances

  /** Monix type-class instances for `cats.Eval`. */
  class MonixCatsEvalInstances extends SuspendableClass[Eval]
    with MemoizableClass[Eval]
    with ComonadClass[Eval]
    with MonadRecClass[Eval] {

    override def pure[A](a: A): Eval[A] = Eval.now(a)
    override def suspend[A](fa: => Eval[A]): Eval[A] = Eval.defer(fa)
    override def evalOnce[A](a: => A): Eval[A] = Eval.later(a)
    override def eval[A](a: => A): Eval[A] = Eval.always(a)
    override def memoize[A](fa: Eval[A]): Eval[A] = fa.memoize

    override def extract[A](x: Eval[A]): A =
      x.value
    override def flatMap[A, B](fa: Eval[A])(f: (A) => Eval[B]): Eval[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Eval[Eval[A]]): Eval[A] =
      ffa.flatMap(x => x)
    override def coflatMap[A, B](fa: Eval[A])(f: (Eval[A]) => B): Eval[B] =
      Eval.always(f(fa))
    override def ap[A, B](ff: Eval[(A) => B])(fa: Eval[A]): Eval[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Eval[A], fb: Eval[B])(f: (A, B) => Z): Eval[Z] =
      for (a <- fa; b <- fb) yield f(a, b)
    override def map[A, B](fa: Eval[A])(f: (A) => B): Eval[B] =
      fa.map(f)
  }

  class MonixCatsFreeInstances[F[_]] extends Suspendable[cats.Fre]
}