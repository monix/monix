/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix

import cats.Contravariant

import scala.concurrent.ExecutionContext

package object execution {

  /** Returns the associated Cats type class instances for the
    * [[CancelableFuture]] data type.
    *
    * @param ec is the
    *        [[scala.concurrent.ExecutionContext ExecutionContext]]
    *        needed in order to create the needed type class instances,
    *        since future transformations rely on an `ExecutionContext`
    *        passed explicitly (by means of an implicit parameter)
    *        on each operation
    */
  implicit def cancelableFutureCatsInstances(implicit ec: ExecutionContext): CancelableFutureCatsInstances =
    new CancelableFutureCatsInstances()

  /** Contravariant type class instance of [[CompletionCallback]] for Cats. */
  implicit val contravariantCompletionCallback: Contravariant[CompletionCallback[*]] =
    new Contravariant[CompletionCallback[*]] {
      override def contramap[A, B](cb: CompletionCallback[A])(f: B => A): CompletionCallback[B] =
        cb.contramap(f)
    }

  @deprecated("Use CompletionCallback", "4.0.0")
  implicit def contravariantCallback[E]: Contravariant[Callback[E, *]] =
    new Contravariant[Callback[E, *]] {
      override def contramap[A, B](cb: Callback[E, A])(f: B => A): Callback[E, B] =
        cb.contramap(f)
    }
}
