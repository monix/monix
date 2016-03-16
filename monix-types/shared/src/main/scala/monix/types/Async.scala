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

package monix.types

import java.util.concurrent.TimeoutException
import cats.Eval
import simulacrum.typeclass
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, implicitConversions}

@typeclass trait Async[F[_]] extends Nondeterminism[F] with Recoverable[F,Throwable] with Zippable[F] {
  /** Trigger a `TimeoutException` after the given `timespan` has passed without
    * the source emitting anything.
    */
  def timeout[A](fa: F[A], timespan: FiniteDuration): F[A] =
    timeoutTo(fa, timespan, Eval.now(raiseError(new TimeoutException(s"After $timespan"))))
}
