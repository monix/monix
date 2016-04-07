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
import monix.eval.TaskEnumerator

trait AsyncIteratorInstances {
  implicit val asyncIteratorInstances: Sequenceable[TaskEnumerator] =
    new MonadFilter[TaskEnumerator] with MonadError[TaskEnumerator, Throwable]
      with CoflatMap[TaskEnumerator] with MonadCombine[TaskEnumerator] {

      def empty[A]: TaskEnumerator[A] =
        TaskEnumerator.empty[A]
      def raiseError[A](e: Throwable): TaskEnumerator[A] =
        TaskEnumerator.error(e)
      def pure[A](x: A): TaskEnumerator[A] =
        TaskEnumerator.now(x)
      override def pureEval[A](x: Eval[A]): TaskEnumerator[A] =
        TaskEnumerator.evalAlways(x.value)

      def flatMap[A, B](fa: TaskEnumerator[A])(f: (A) => TaskEnumerator[B]): TaskEnumerator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: TaskEnumerator[A])(f: (TaskEnumerator[A]) => B): TaskEnumerator[B] =
        TaskEnumerator.evalAlways(f(fa))
      override def coflatten[A](fa: TaskEnumerator[A]): TaskEnumerator[TaskEnumerator[A]] =
        TaskEnumerator.now(fa)
      def handleErrorWith[A](fa: TaskEnumerator[A])(f: (Throwable) => TaskEnumerator[A]): TaskEnumerator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: TaskEnumerator[A])(f: (A) => Boolean): TaskEnumerator[A] =
        fa.filter(f)
      override def map[A, B](fa: TaskEnumerator[A])(f: (A) => B): TaskEnumerator[B] =
        fa.map(f)
      override def handleError[A](fa: TaskEnumerator[A])(f: (Throwable) => A): TaskEnumerator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: TaskEnumerator[A], y: TaskEnumerator[A]): TaskEnumerator[A] =
        x ++ y
    }
}
