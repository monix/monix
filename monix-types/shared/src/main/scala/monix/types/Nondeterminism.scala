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

import simulacrum.typeclass
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, implicitConversions}

@typeclass trait Nondeterminism[F[_]] {
  /** Given a list of non-deterministic structures, mirrors the
    * first that manages to emit an element or that completes and
    * ignore or cancel the rest.
    */
  def firstStartedOf[A](seq: Seq[F[A]]): F[A]

  /** Delays the execution of the instance and consequently the execution of
    * any side-effects, by the specified `timespan`.
    */
  def delayExecution[A](fa: F[A], timespan: FiniteDuration): F[A]

  /** Delays the execution of the instance and consequently the execution of
    * any side-effects, until the given `trigger` emits an element or completes.
    */
  def delayExecutionWith[A,B](fa: F[A], trigger: F[B]): F[A]

  /** Executes the source immediately, but delays the signaling by
    * the specified `timespan`. In case `F` is a sequence,
    * then the delay will be applied to each element, but not
    * to completion or the signaling of an error.
    */
  def delayResult[A](fa: F[A], timespan: FiniteDuration): F[A]

  /** Executes the source immediately, but delays the signaling
    * until the specified `selector` emits an element or completes.
    * In case `F` is a sequence, then the delay will be applied
    * to each element, but not to completion or the
    * signaling of an error.
    */
  def delayResultBySelector[A,B](fa: F[A])(selector: A => F[B]): F[A]
}
