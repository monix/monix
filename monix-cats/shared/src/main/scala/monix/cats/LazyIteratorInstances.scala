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
import monix.eval.CoevalIterator

trait LazyIteratorInstances {
  implicit val lazyIteratorInstances: Sequenceable[CoevalIterator] =
    new MonadFilter[CoevalIterator] with MonadError[CoevalIterator, Throwable]
      with CoflatMap[CoevalIterator] with MonadCombine[CoevalIterator] {

      def empty[A]: CoevalIterator[A] =
        CoevalIterator.empty[A]
      def raiseError[A](e: Throwable): CoevalIterator[A] =
        CoevalIterator.error(e)
      def pure[A](x: A): CoevalIterator[A] =
        CoevalIterator.now(x)
      override def pureEval[A](x: Eval[A]): CoevalIterator[A] =
        CoevalIterator.evalAlways(x.value)

      def flatMap[A, B](fa: CoevalIterator[A])(f: (A) => CoevalIterator[B]): CoevalIterator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: CoevalIterator[A])(f: (CoevalIterator[A]) => B): CoevalIterator[B] =
        CoevalIterator.evalAlways(f(fa))
      override def coflatten[A](fa: CoevalIterator[A]): CoevalIterator[CoevalIterator[A]] =
        CoevalIterator.now(fa)
      def handleErrorWith[A](fa: CoevalIterator[A])(f: (Throwable) => CoevalIterator[A]): CoevalIterator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: CoevalIterator[A])(f: (A) => Boolean): CoevalIterator[A] =
        fa.filter(f)
      override def map[A, B](fa: CoevalIterator[A])(f: (A) => B): CoevalIterator[B] =
        fa.map(f)
      override def handleError[A](fa: CoevalIterator[A])(f: (Throwable) => A): CoevalIterator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: CoevalIterator[A], y: CoevalIterator[A]): CoevalIterator[A] =
        x ++ y
    }
}