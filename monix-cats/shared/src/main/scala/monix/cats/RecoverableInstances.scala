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

import monix.types.Recoverable
import language.higherKinds

/** Converts Monix's Recoverable into the Cats MonadError. */
trait RecoverableInstances extends MonadInstances {
  implicit def monixRecoverableInstances[F[_] : Recoverable]: _root_.cats.MonadError[F,Throwable] =
    new ConvertMonixRecoverableToCats[F]()

  class ConvertMonixRecoverableToCats[F[_]](implicit F: Recoverable[F])
    extends ConvertMonixMonadToCats[F] with _root_.cats.MonadError[F,Throwable] {

    def raiseError[A](e: Throwable): F[A] = F.error(e)
    def handleErrorWith[A](fa: F[A])(f: (Throwable) => F[A]): F[A] =
      F.onErrorHandleWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (Throwable) => A): F[A] =
      F.onErrorHandle(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[Throwable, A]): F[A] =
      F.onErrorRecover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[Throwable, F[A]]): F[A] =
      F.onErrorRecoverWith(fa)(pf)
  }
}
