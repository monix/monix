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
import monix.eval.AsyncIterator

trait AsyncIteratorInstances {
  implicit val asyncIteratorInstances: Sequenceable[AsyncIterator] =
    new MonadFilter[AsyncIterator] with MonadError[AsyncIterator, Throwable]
      with CoflatMap[AsyncIterator] with MonadCombine[AsyncIterator] {

      def empty[A]: AsyncIterator[A] =
        AsyncIterator.empty[A]
      def raiseError[A](e: Throwable): AsyncIterator[A] =
        AsyncIterator.error(e)
      def pure[A](x: A): AsyncIterator[A] =
        AsyncIterator.now(x)
      override def pureEval[A](x: Eval[A]): AsyncIterator[A] =
        AsyncIterator.evalAlways(x.value)

      def flatMap[A, B](fa: AsyncIterator[A])(f: (A) => AsyncIterator[B]): AsyncIterator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: AsyncIterator[A])(f: (AsyncIterator[A]) => B): AsyncIterator[B] =
        AsyncIterator.evalAlways(f(fa))
      override def coflatten[A](fa: AsyncIterator[A]): AsyncIterator[AsyncIterator[A]] =
        AsyncIterator.now(fa)
      def handleErrorWith[A](fa: AsyncIterator[A])(f: (Throwable) => AsyncIterator[A]): AsyncIterator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: AsyncIterator[A])(f: (A) => Boolean): AsyncIterator[A] =
        fa.filter(f)
      override def map[A, B](fa: AsyncIterator[A])(f: (A) => B): AsyncIterator[B] =
        fa.map(f)
      override def handleError[A](fa: AsyncIterator[A])(f: (Throwable) => A): AsyncIterator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: AsyncIterator[A], y: AsyncIterator[A]): AsyncIterator[A] =
        x ++ y
    }
}
