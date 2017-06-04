/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.reactive
package instances

import cats.{CoflatMap, MonadError, MonadFilter, MonoidK}

/** Specification for Cats type classes, to be implemented by
  * asynchronous sequences, like [[Observable]].
  */
trait CatsAsyncSeqInstances[F[_]] extends MonadError[F, Throwable]
  with MonadFilter[F]
  with MonoidK[F]
  with CoflatMap[F]

object CatsObservableInstances {
  /** Cats instances for [[Observable]]. */
  class ForObservable extends CatsAsyncSeqInstances[Observable] {
    override def pure[A](a: A): Observable[A] = Observable.now(a)
    val unit: Observable[Unit] = Observable.now(())

    override def combineK[A](x: Observable[A], y: Observable[A]): Observable[A] =
      x ++ y
    override def flatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Observable[Observable[A]]): Observable[A] =
      ffa.flatten
    override def tailRecM[A, B](a: A)(f: (A) => Observable[Either[A, B]]): Observable[B] =
      Observable.tailRecM(a)(f)
    override def coflatMap[A, B](fa: Observable[A])(f: (Observable[A]) => B): Observable[B] =
      Observable.eval(f(fa))
    override def ap[A, B](ff: Observable[(A) => B])(fa: Observable[A]): Observable[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Observable[A], fb: Observable[B])(f: (A, B) => Z): Observable[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Observable[A] =
      Observable.raiseError(e)
    override def handleError[A](fa: Observable[A])(f: (Throwable) => A): Observable[A] =
      fa.onErrorHandle(f)
    override def handleErrorWith[A](fa: Observable[A])(f: (Throwable) => Observable[A]): Observable[A] =
      fa.onErrorHandleWith(f)
    override def recover[A](fa: Observable[A])(pf: PartialFunction[Throwable, A]): Observable[A] =
      fa.onErrorRecover(pf)
    override def recoverWith[A](fa: Observable[A])(pf: PartialFunction[Throwable, Observable[A]]): Observable[A] =
      fa.onErrorRecoverWith(pf)
    override def empty[A]: Observable[A] =
      Observable.empty[A]
    override def filter[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
      fa.filter(f)
  }

  /** Reusable instance of [[ForObservable]]. */
  object ForObservable extends ForObservable
}