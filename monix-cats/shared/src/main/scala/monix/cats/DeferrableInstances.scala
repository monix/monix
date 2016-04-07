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

import _root_.cats.{CoflatMap, Eval, Later, MonadError, Now}
import monix.types.Deferrable

/** Converts Monix's Monad into the Cats monad. */
trait DeferrableInstances extends RecoverableInstances {
  implicit def monixDeferrableToCats[F[_] : Deferrable]: MonadError[F,Throwable] with CoflatMap[F] =
    new ConvertMonixDeferrableToCats[F]()

  class ConvertMonixDeferrableToCats[F[_]](implicit F: Deferrable[F])
    extends ConvertMonixRecoverableToCats[F] with MonadError[F,Throwable] with CoflatMap[F] {

    def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.now(f(fa))
    override def coflatten[A](fa: F[A]): F[F[A]] = F.now(fa)

    override def pureEval[A](x: Eval[A]): F[A] =
      x match {
        case Now(a) => F.now(a)
        case later: Later[_] => F.evalOnce(later.asInstanceOf[Eval[A]].value)
        case other => F.evalAlways(other.value)
      }
  }
}
