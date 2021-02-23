/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.catnap.internal

import cats.implicits._
import cats.effect.{Async, ExitCase}
import monix.catnap.FutureLift
import monix.execution.Callback
import scala.concurrent.Promise

private[monix] object AsyncUtils {
  /**
    * Describing the `cancelable` builder for any `Async` data type,
    * in terms of `bracket`.
    */
  def cancelable[F[_], A](k: (Either[Throwable, A] => Unit) => F[Unit])(implicit F: Async[F]): F[A] =
    F.asyncF { cb =>
      val p = Promise[A]()
      val awaitPut = Callback.fromPromise(p)
      val future = p.future
      val futureF = FutureLift.scalaToAsync(F.pure(future)).attempt.map(cb)
      val cancel = k(awaitPut)

      F.guaranteeCase(futureF) {
        case ExitCase.Canceled => cancel
        case _ => F.unit
      }
    }
}
