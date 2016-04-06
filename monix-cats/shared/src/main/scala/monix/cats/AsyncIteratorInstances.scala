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
import monix.eval.TaskIterator

trait AsyncIteratorInstances {
  implicit val asyncIteratorInstances: Sequenceable[TaskIterator] =
    new MonadFilter[TaskIterator] with MonadError[TaskIterator, Throwable]
      with CoflatMap[TaskIterator] with MonadCombine[TaskIterator] {

      def empty[A]: TaskIterator[A] =
        TaskIterator.empty[A]
      def raiseError[A](e: Throwable): TaskIterator[A] =
        TaskIterator.error(e)
      def pure[A](x: A): TaskIterator[A] =
        TaskIterator.now(x)
      override def pureEval[A](x: Eval[A]): TaskIterator[A] =
        TaskIterator.evalAlways(x.value)

      def flatMap[A, B](fa: TaskIterator[A])(f: (A) => TaskIterator[B]): TaskIterator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: TaskIterator[A])(f: (TaskIterator[A]) => B): TaskIterator[B] =
        TaskIterator.evalAlways(f(fa))
      override def coflatten[A](fa: TaskIterator[A]): TaskIterator[TaskIterator[A]] =
        TaskIterator.now(fa)
      def handleErrorWith[A](fa: TaskIterator[A])(f: (Throwable) => TaskIterator[A]): TaskIterator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: TaskIterator[A])(f: (A) => Boolean): TaskIterator[A] =
        fa.filter(f)
      override def map[A, B](fa: TaskIterator[A])(f: (A) => B): TaskIterator[B] =
        fa.map(f)
      override def handleError[A](fa: TaskIterator[A])(f: (Throwable) => A): TaskIterator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: TaskIterator[A], y: TaskIterator[A]): TaskIterator[A] =
        x ++ y
    }
}
