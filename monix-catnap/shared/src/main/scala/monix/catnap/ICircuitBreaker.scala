/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.effect.{Async, Concurrent}

import scala.concurrent.duration.{Duration, FiniteDuration}

trait ICircuitBreaker[F[_]] {
  def protect[A](task: F[A]): F[A]
  def maxFailures: Int
  def resetTimeout: FiniteDuration
  def exponentialBackoffFactor: Double
  def maxResetTimeout: Duration
  def state: F[CircuitBreaker.State]
  def awaitClose(implicit F: Concurrent[F] OrElse Async[F]): F[Unit]
  def doOnClosed(callback: F[Unit]): ICircuitBreaker[F]
  def doOnHalfOpen(callback: F[Unit]): ICircuitBreaker[F]
  def doOnOpen(callback: F[Unit]): ICircuitBreaker[F]
  def doOnRejectedTask(callback: F[Unit]): ICircuitBreaker[F]
}
