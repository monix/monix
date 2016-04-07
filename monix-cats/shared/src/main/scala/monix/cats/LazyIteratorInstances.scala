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

import cats._
import monix.eval.CoevalEnumerator

trait LazyIteratorInstances {
  implicit val lazyIteratorInstances: Sequenceable[CoevalEnumerator] =
    new MonadFilter[CoevalEnumerator] with MonadError[CoevalEnumerator, Throwable]
      with CoflatMap[CoevalEnumerator] with MonadCombine[CoevalEnumerator] {

      def empty[A]: CoevalEnumerator[A] =
        CoevalEnumerator.empty[A]
      def raiseError[A](e: Throwable): CoevalEnumerator[A] =
        CoevalEnumerator.error(e)
      def pure[A](x: A): CoevalEnumerator[A] =
        CoevalEnumerator.now(x)
      override def pureEval[A](x: Eval[A]): CoevalEnumerator[A] =
        CoevalEnumerator.evalAlways(x.value)

      def flatMap[A, B](fa: CoevalEnumerator[A])(f: (A) => CoevalEnumerator[B]): CoevalEnumerator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: CoevalEnumerator[A])(f: (CoevalEnumerator[A]) => B): CoevalEnumerator[B] =
        CoevalEnumerator.evalAlways(f(fa))
      override def coflatten[A](fa: CoevalEnumerator[A]): CoevalEnumerator[CoevalEnumerator[A]] =
        CoevalEnumerator.now(fa)
      def handleErrorWith[A](fa: CoevalEnumerator[A])(f: (Throwable) => CoevalEnumerator[A]): CoevalEnumerator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: CoevalEnumerator[A])(f: (A) => Boolean): CoevalEnumerator[A] =
        fa.filter(f)
      override def map[A, B](fa: CoevalEnumerator[A])(f: (A) => B): CoevalEnumerator[B] =
        fa.map(f)
      override def handleError[A](fa: CoevalEnumerator[A])(f: (Throwable) => A): CoevalEnumerator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: CoevalEnumerator[A], y: CoevalEnumerator[A]): CoevalEnumerator[A] =
        x ++ y
    }
}