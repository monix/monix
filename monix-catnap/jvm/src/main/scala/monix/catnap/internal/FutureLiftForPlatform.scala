/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.catnap
package internal

import java.util.concurrent.{CancellationException, CompletableFuture, CompletionException}
import java.util.function.BiFunction
import cats.effect.{Async, Concurrent}

private[catnap] abstract class FutureLiftForPlatform {
  /**
    * Lifts Java's `java.util.concurrent.CompletableFuture` to
    * any data type implementing `cats.effect.Concurrent`.
    */
  implicit def javaCompletableToAsync[F[_]](implicit F: Async[F]): FutureLift[F, CompletableFuture] =
    new FutureLift[F, CompletableFuture] {
      override def apply[A](fa: F[CompletableFuture[A]]): F[A] =
        F.flatMap(fa) { cf =>
          F.async { cb =>
            F.delay {
              subscribeToCompletable(cf, cb)
              Some(F.delay { cf.cancel(true); () })
            }
          }
        }
    }


  private def subscribeToCompletable[A, F[_]](cf: CompletableFuture[A], cb: Either[Throwable, A] => Unit): Unit = {
    cf.handle[Unit](new BiFunction[A, Throwable, Unit] {
      override def apply(result: A, err: Throwable): Unit = {
        err match {
          case null =>
            cb(Right(result))
          case _: CancellationException =>
            ()
          case ex: CompletionException if ex.getCause ne null =>
            cb(Left(ex.getCause))
          case ex =>
            cb(Left(ex))
        }
      }
    })
    ()
  }
}
