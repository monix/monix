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
import monix.reactive.Observable

trait ObservableInstances {
  implicit val observableInstances: Sequenceable[Observable] =
    new MonadFilter[Observable] with MonadError[Observable, Throwable]
      with CoflatMap[Observable] with MonadCombine[Observable] {

      def flatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: Observable[A])(f: (Observable[A]) => B): Observable[B] =
        Observable.evalAlways(f(fa))
      override def coflatten[A](fa: Observable[A]): Observable[Observable[A]] =
        Observable.now(fa)
      def handleErrorWith[A](fa: Observable[A])(f: (Throwable) => Observable[A]): Observable[A] =
        fa.onErrorHandleWith(f)
      def raiseError[A](e: Throwable): Observable[A] =
        Observable.error(e)
      def pure[A](x: A): Observable[A] =
        Observable.now(x)

      override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
        fa.map(f)
      override def handleError[A](fa: Observable[A])(f: (Throwable) => A): Observable[A] =
        fa.onErrorHandle(f)
      override def pureEval[A](x: Eval[A]): Observable[A] =
        Observable.evalAlways(x.value)

      def empty[A]: Observable[A] =
        Observable.empty[A]
      override def filter[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
        fa.filter(f)

      def combineK[A](x: Observable[A], y: Observable[A]): Observable[A] =
        x ++ y
    }
}