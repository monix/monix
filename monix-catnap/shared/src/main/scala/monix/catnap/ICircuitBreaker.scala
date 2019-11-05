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

/** The circuit breaker is used to provide stability and prevent
  * cascading failures in distributed systems.
  *
  * @see [[monix.catnap.CircuitBreaker]]
  */
trait ICircuitBreaker[F[_]] {
  /** Returns a new task that upon execution will execute the given
    * task, but with the protection of this circuit breaker.
    */
  def protect[A](task: F[A]): F[A]

  /** The maximum count for allowed failures before opening the circuit breaker. */
  def maxFailures: Int

  /** The timespan to wait in the `Open` state before attempting
    * a close of the circuit breaker (but without the backoff
    * factor applied).
    *
    * If we have a specified [[exponentialBackoffFactor]] then the
    * actual reset timeout applied will be this value multiplied
    * repeatedly with that factor, a value that can be found by
    * querying the [[state]].
    */
  def resetTimeout: FiniteDuration

  /** A factor to use for resetting the [[resetTimeout]] when in the
    * `HalfOpen` state, in case the attempt for `Close` fails.
    */
  def exponentialBackoffFactor: Double

  /** The maximum timespan the circuit breaker is allowed to use
    * as a [[resetTimeout]] when applying the [[exponentialBackoffFactor]].
    */
  def maxResetTimeout: Duration

  /** Returns the current [[CircuitBreaker.State]], meant for
    * debugging purposes.
    */
  def state: F[CircuitBreaker.State]

  /**
    * Awaits for this `CircuitBreaker` to be [[CircuitBreaker.Closed closed]].
    *
    * This only works if the type class instance used is implementing
    * [[https://typelevel.org/cats-effect/typeclasses/async.html cats.effect.Async]].
    *
    * If this `CircuitBreaker` is already in a closed state, then
    * it returns immediately, otherwise it will wait (asynchronously) until
    * the `CircuitBreaker` switches to the [[CircuitBreaker.Closed Closed]]
    * state again.
    *
    * @param F is a restriction for `F[_]` to implement
    *        `Concurrent[F]` or `Async[F]` (from Cats-Effect). If it
    *        implements `Concurrent`, then the resulting instance will
    *        be cancelable, to properly dispose of the registered
    *        listener in case of cancellation.
    */
  def awaitClose(implicit F: Concurrent[F] OrElse Async[F]): F[Unit]

  /** Returns a new circuit breaker that wraps the state of the source
    * and that will fire the given callback upon the circuit breaker
    * transitioning to the [[CircuitBreaker.Closed Closed]] state.
    *
    * Useful for gathering stats.
    *
    * NOTE: calling this method multiple times will create a circuit
    * breaker that will call multiple callbacks, thus the callback
    * given is cumulative with other specified callbacks.
    *
    * @param callback is to be executed when the state evolves into `Closed`
    * @return a new circuit breaker wrapping the state of the source
    */
  def doOnClosed(callback: F[Unit]): ICircuitBreaker[F]

  /** Returns a new circuit breaker that wraps the state of the source
    * and that will fire the given callback upon the circuit breaker
    * transitioning to the [[CircuitBreaker.HalfOpen HalfOpen]]
    * state.
    *
    * Useful for gathering stats.
    *
    * NOTE: calling this method multiple times will create a circuit
    * breaker that will call multiple callbacks, thus the callback
    * given is cumulative with other specified callbacks.
    *
    * @param callback is to be executed when the state evolves into `HalfOpen`
    * @return a new circuit breaker wrapping the state of the source
    */
  def doOnHalfOpen(callback: F[Unit]): ICircuitBreaker[F]

  /** Returns a new circuit breaker that wraps the state of the source
    * and that will fire the given callback upon the circuit breaker
    * transitioning to the [[CircuitBreaker.Open Open]] state.
    *
    * Useful for gathering stats.
    *
    * NOTE: calling this method multiple times will create a circuit
    * breaker that will call multiple callbacks, thus the callback
    * given is cumulative with other specified callbacks.
    *
    * @param callback is to be executed when the state evolves into `Open`
    * @return a new circuit breaker wrapping the state of the source
    */
  def doOnOpen(callback: F[Unit]): ICircuitBreaker[F]

  /** Returns a new circuit breaker that wraps the state of the source
    * and that upon a task being rejected will execute the given
    * `callback`.
    *
    * Useful for gathering stats.
    *
    * NOTE: calling this method multiple times will create a circuit
    * breaker that will call multiple callbacks, thus the callback
    * given is cumulative with other specified callbacks.
    *
    * @param callback is to be executed when tasks get rejected
    * @return a new circuit breaker wrapping the state of the source
    */
  def doOnRejectedTask(callback: F[Unit]): ICircuitBreaker[F]
}
