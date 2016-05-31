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

/** Converts Monix's [[Recoverable]] into the Cats MonadError. */
trait RecoverableInstances extends ShimsInstances {
  class ConvertMonixRecoverableToCats[F[_],E](implicit F: Recoverable[F,E])
    extends ConvertMonixMonadToCats[F] with _root_.cats.MonadError[F,E] {

    def raiseError[A](e: E): F[A] = F.raiseError(e)
    def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.onErrorHandleWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (E) => A): F[A] =
      F.onErrorHandle(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      F.onErrorRecover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      F.onErrorRecoverWith(fa)(pf)
  }
}
