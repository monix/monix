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

import cats.effect.{Async, Concurrent, Clock, ExitCase, Sync}
import cats.implicits._
import monix.execution.CancelablePromise
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.{Atomic, AtomicAny, PaddingStrategy}
import monix.execution.exceptions.ExecutionRejectedException
import monix.execution.internal.Constants
import scala.annotation.tailrec
import scala.concurrent.duration._

/** The `CircuitBreaker` is used to provide stability and prevent
  * cascading failures in distributed systems.
  *
  * =Purpose=
  *
  * As an example, we have a web application interacting with a remote
  * third party web service. Let's say the third party has oversold
  * their capacity and their database melts down under load. Assume
  * that the database fails in such a way that it takes a very long
  * time to hand back an error to the third party web service. This in
  * turn makes calls fail after a long period of time.  Back to our
  * web application, the users have noticed that their form
  * submissions take much longer seeming to hang. Well the users do
  * what they know to do which is use the refresh button, adding more
  * requests to their already running requests.  This eventually
  * causes the failure of the web application due to resource
  * exhaustion. This will affect all users, even those who are not
  * using functionality dependent on this third party web service.
  *
  * Introducing circuit breakers on the web service call would cause
  * the requests to begin to fail-fast, letting the user know that
  * something is wrong and that they need not refresh their
  * request. This also confines the failure behavior to only those
  * users that are using functionality dependent on the third party,
  * other users are no longer affected as there is no resource
  * exhaustion. Circuit breakers can also allow savvy developers to
  * mark portions of the site that use the functionality unavailable,
  * or perhaps show some cached content as appropriate while the
  * breaker is open.
  *
  * =How It Works=
  *
  * The circuit breaker models a concurrent state machine that
  * can be in any of these 3 states:
  *
  *  1. [[monix.catnap.CircuitBreaker.Closed Closed]]: During normal
  *     operations or when the `CircuitBreaker` starts
  *    - Exceptions increment the `failures` counter
  *    - Successes reset the failure count to zero
  *    - When the `failures` counter reaches the `maxFailures` count,
  *      the breaker is tripped into `Open` state
  *
  *  1. [[monix.catnap.CircuitBreaker.Open Open]]: The circuit breaker
  *     rejects all tasks with an
  *     [[monix.execution.exceptions.ExecutionRejectedException ExecutionRejectedException]]
  *    - all tasks fail fast with `ExecutionRejectedException`
  *    - after the configured `resetTimeout`, the circuit breaker
  *      enters a [[monix.catnap.CircuitBreaker.HalfOpen HalfOpen]] state,
  *      allowing one task to go through for testing the connection
  *
  *  1. [[monix.catnap.CircuitBreaker.HalfOpen HalfOpen]]: The circuit breaker
  *     has already allowed a task to go through, as a reset attempt,
  *     in order to test the connection
  *    - The first task when `Open` has expired is allowed through
  *      without failing fast, just before the circuit breaker is
  *      evolved into the `HalfOpen` state
  *    - All tasks attempted in `HalfOpen` fail-fast with an exception
  *      just as in [[monix.catnap.CircuitBreaker.Open Open]] state
  *    - If that task attempt succeeds, the breaker is reset back to
  *      the `Closed` state, with the `resetTimeout` and the
  *      `failures` count also reset to initial values
  *    - If the first call fails, the breaker is tripped again into
  *      the `Open` state (the `resetTimeout` is multiplied by the
  *      exponential backoff factor)
  *
  * =Usage=
  *
  * {{{
  *   import monix.catnap._
  *   import scala.concurrent.duration._
  *
  *   // Using cats.effect.IO for this sample, but you can use any effect
  *   // type that integrates with Cats-Effect, including monix.eval.Task:
  *   import cats.effect.{Clock, IO}
  *   implicit val clock = Clock.create[IO]
  *
  *   // Using the "unsafe" builder for didactic purposes, but prefer
  *   // the safe "apply" builder:
  *   val circuitBreaker = CircuitBreaker[IO].unsafe(
  *     maxFailures = 5,
  *     resetTimeout = 10.seconds
  *   )
  *
  *   //...
  *   val problematic = IO {
  *     val nr = util.Random.nextInt()
  *     if (nr % 2 == 0) nr else
  *       throw new RuntimeException("dummy")
  *   }
  *
  *   val task = circuitBreaker.protect(problematic)
  * }}}
  *
  * When attempting to close the circuit breaker and resume normal
  * operations, we can also apply an exponential backoff for repeated
  * failed attempts, like so:
  *
  * {{{
  *   val exponential = CircuitBreaker[IO].of(
  *     maxFailures = 5,
  *     resetTimeout = 10.seconds,
  *     exponentialBackoffFactor = 2,
  *     maxResetTimeout = 10.minutes
  *   )
  * }}}
  *
  * In this sample we attempt to reconnect after 10 seconds, then after
  * 20, 40 and so on, a delay that keeps increasing up to a configurable
  * maximum of 10 minutes.
  *
  * ==Sync versus Async==
  *
  * The `CircuitBreaker` works with both
  * [[https://typelevel.org/cats-effect/typeclasses/sync.html Sync]] and
  * [[https://typelevel.org/cats-effect/typeclasses/async.html Async]]
  * type class instances.
  *
  * If the `F[_]` type used implements `Async`, then the `CircuitBreaker`
  * gains the ability to wait for it to be closed, via
  * [[CircuitBreaker!.awaitClose awaitClose]].
  *
  * ==Retrying Tasks==
  *
  * Generally it's best if tasks are retried with an exponential back-off
  * strategy for async tasks.
  *
  * {{{
  *   import cats.implicits._
  *   import cats.effect._
  *   import monix.execution.exceptions.ExecutionRejectedException
  *
  *   def protectWithRetry[F[_], A](task: F[A], cb: CircuitBreaker[F], delay: FiniteDuration)
  *     (implicit F: Async[F], timer: Timer[F]): F[A] = {
  *
  *     cb.protect(task).recoverWith {
  *       case _: ExecutionRejectedException =>
  *         // Sleep, then retry
  *         timer.sleep(delay).flatMap(_ => protectWithRetry(task, cb, delay * 2))
  *     }
  *   }
  * }}}
  *
  * But an alternative is to wait for the precise moment at which the
  * `CircuitBreaker` is closed again and you can do so via the
  * [[CircuitBreaker!.awaitClose awaitClose]] method:
  *
  * {{{
  *   def protectWithRetry2[F[_], A](task: F[A], cb: CircuitBreaker[F])
  *     (implicit F: Async[F]): F[A] = {
  *
  *     cb.protect(task).recoverWith {
  *       case _: ExecutionRejectedException =>
  *         // Waiting for the CircuitBreaker to close, then retry
  *         cb.awaitClose.flatMap(_ => protectWithRetry2(task, cb))
  *     }
  *   }
  * }}}
  *
  * Be careful when doing this, plan carefully, because you might end up with the
  * "[[https://en.wikipedia.org/wiki/Thundering_herd_problem thundering herd problem]]".
  *
  * =Credits=
  *
  * This Monix data type was inspired by the availability of
  * [[http://doc.akka.io/docs/akka/current/common/circuitbreaker.html Akka's Circuit Breaker]].
  */
final class CircuitBreaker[F[_]] private (
  _stateRef: AtomicAny[CircuitBreaker.State],
  _maxFailures: Int,
  _resetTimeout: FiniteDuration,
  _exponentialBackoffFactor: Double,
  _maxResetTimeout: Duration,
  onRejected: F[Unit],
  onClosed: F[Unit],
  onHalfOpen: F[Unit],
  onOpen: F[Unit])
  (implicit F: Sync[F], clock: Clock[F]) {

  require(_maxFailures >= 0, "maxFailures >= 0")
  require(_exponentialBackoffFactor >= 1, "exponentialBackoffFactor >= 1")
  require(_resetTimeout > Duration.Zero, "resetTimeout > 0")
  require(_maxResetTimeout > Duration.Zero, "maxResetTimeout > 0")

  import monix.catnap.CircuitBreaker._
  private[this] val stateRef = _stateRef

  /**
    * The maximum count for allowed failures before opening the circuit breaker.
    */
  val maxFailures = _maxFailures

  /** The timespan to wait in the `Open` state before attempting
    * a close of the circuit breaker (but without the backoff
    * factor applied).
    *
    * If we have a specified [[exponentialBackoffFactor]] then the
    * actual reset timeout applied will be this value multiplied
    * repeatedly with that factor, a value that can be found by
    * querying the [[state]].
    */
  val resetTimeout = _resetTimeout

  /** A factor to use for resetting the [[resetTimeout]] when in the
    * `HalfOpen` state, in case the attempt for `Close` fails.
    */
  val exponentialBackoffFactor = _exponentialBackoffFactor

  /** The maximum timespan the circuit breaker is allowed to use
    * as a [[resetTimeout]] when applying the [[exponentialBackoffFactor]].
    */
  val maxResetTimeout = _maxResetTimeout

  /** Returns the current [[CircuitBreaker.State]], meant for
    * debugging purposes.
    */
  val state: F[CircuitBreaker.State] =
    F.delay(stateRef.get)

  /** Returns a new task that upon execution will execute the given
    * task, but with the protection of this circuit breaker.
    */
  def protect[A](task: F[A]): F[A] =
    F.suspend(unsafeProtect(task))

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
  def awaitClose(implicit F: Concurrent[F] OrElse Async[F]): F[Unit] = {
    val F0 = F.unify
    F0.suspend {
      stateRef.get match {
        case ref: Open =>
          FutureLift.scalaToConcurrentOrAsync(F0.pure(ref.awaitClose.future))
        case ref: HalfOpen =>
          FutureLift.scalaToConcurrentOrAsync(F0.pure(ref.awaitClose.future))
        case _ =>
          F0.unit
      }
    }
  }

  /** Function for counting failures in the `Closed` state,
    * triggering the `Open` state if necessary.
    */
  private[this] val maybeMarkOrResetFailures: (Either[Throwable, Any] => F[Any]) = {
    // Reschedule logic, for retries that come after a `Clock` query
    // and that can no longer be tail-recursive
    def reschedule[A](exit: Either[Throwable, A]): F[A] =
      markFailure(exit)

    // Recursive function because of going into CAS loop
    @tailrec def markFailure[A](result: Either[Throwable, A]): F[A] =
      stateRef.get match {
        case current @ Closed(failures) =>
          result match {
            case Right(a) =>
              // In case of success, must reset the failures counter!
              if (failures == 0) F.pure(a) else {
                val update = Closed(0)
                if (!stateRef.compareAndSet(current, update))
                  markFailure(result) // retry?
                else
                  F.pure(a)
              }

            case Left(error) =>
              // In case of failure, we either increment the failures counter,
              // or we transition in the `Open` state.
              if (failures+1 < maxFailures) {
                // It's fine, just increment the failures count
                val update = Closed(failures + 1)
                if (!stateRef.compareAndSet(current, update))
                  markFailure(result) // retry?
                else
                  F.raiseError(error)
              }
              else {
                // N.B. this could be canceled, however we don't care
                clock.monotonic(MILLISECONDS).flatMap { now =>
                  // We've gone over the permitted failures threshold,
                  // so we need to open the circuit breaker
                  val update = Open(now, resetTimeout, CancelablePromise())

                  if (!stateRef.compareAndSet(current, update))
                    reschedule(result) // retry
                  else
                    onOpen.flatMap(_ => F.raiseError(error))
                }
              }
          }

        case _ =>
          // Concurrent execution of another handler happened, we are
          // already in an Open state, so not doing anything extra
          F.fromEither(result)
      }

    markFailure
  }

  /** Internal function that is the handler for the reset attempt when
    * the circuit breaker is in `HalfOpen`. In this state we can
    * either transition to `Closed` in case the attempt was
    * successful, or to `Open` again, in case the attempt failed.
    *
    * @param task is the task to execute, along with the attempt
    *        handler attached
    * @param resetTimeout is the last timeout applied to the previous
    *        `Open` state, to be multiplied by the backoff factor in
    *        case the attempt fails and it needs to transition to
    *        `Open` again
    */
  private def attemptReset[A](task: F[A], resetTimeout: FiniteDuration, await: CancelablePromise[Unit]): F[A] =
    F.bracketCase(onHalfOpen)(_ => task) { (_, exit) =>
      exit match {
        case ExitCase.Canceled =>
          // A canceled task isn't interesting, ignoring:
          F.unit

        case ExitCase.Completed =>
          // While in HalfOpen only a reset attempt is allowed to update
          // the state, so setting this directly is safe
          stateRef.set(Closed(0))
          await.complete(Constants.successOfUnit)
          onClosed

        case ExitCase.Error(_) =>
          // Failed reset, which means we go back in the Open state with new expiry
          val nextTimeout = {
            val value = (resetTimeout.toMillis * exponentialBackoffFactor).millis
            if (maxResetTimeout.isFinite && value > maxResetTimeout)
              maxResetTimeout.asInstanceOf[FiniteDuration]
            else
              value
          }

          clock.monotonic(MILLISECONDS).flatMap { ts =>
            stateRef.set(Open(ts, nextTimeout, await))
            onOpen
          }
      }
    }

  private def unsafeProtect[A](task: F[A]): F[A] =
    stateRef.get match {
      case Closed(_) =>
        val bind = maybeMarkOrResetFailures.asInstanceOf[Either[Throwable, A] => F[A]]
        task.attempt.flatMap(bind)

      case current: Open =>
        clock.monotonic(MILLISECONDS).flatMap { now =>
          val expiresAt = current.expiresAt
          val timeout = current.resetTimeout
          val await = current.awaitClose

          if (now >= expiresAt) {
            // The Open state has expired, so we are letting just one
            // task to execute, while transitioning into HalfOpen
            if (!stateRef.compareAndSet(current, HalfOpen(timeout, await)))
              unsafeProtect(task) // retry!
            else
              attemptReset(task, timeout, await)
          }
          else {
            // Open isn't expired, so we need to fail
            val expiresInMillis = expiresAt - now
            onRejected.flatMap { _ =>
              F.raiseError(ExecutionRejectedException(
                "Rejected because the CircuitBreaker is in the Open state, " +
                s"attempting to close in $expiresInMillis millis"
              ))
            }
          }
        }

      case _ =>
        // CircuitBreaker is in HalfOpen state, which means we still reject all
        // tasks, while waiting to see if our reset attempt succeeds or fails
        onRejected.flatMap { _ =>
          F.raiseError(ExecutionRejectedException(
            "Rejected because the CircuitBreaker is in the HalfOpen state"
          ))
        }
    }

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
  def doOnRejectedTask(callback: F[Unit]): CircuitBreaker[F] = {
    val onRejected = this.onRejected.flatMap(_ => callback)
    new CircuitBreaker(
      _stateRef = stateRef,
      _maxFailures = maxFailures,
      _resetTimeout = resetTimeout,
      _exponentialBackoffFactor = exponentialBackoffFactor,
      _maxResetTimeout = maxResetTimeout,
      onRejected = onRejected,
      onClosed = onClosed,
      onHalfOpen = onHalfOpen,
      onOpen = onOpen)
  }

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
  def doOnClosed(callback: F[Unit]): CircuitBreaker[F] = {
    val onClosed = this.onClosed.flatMap(_ => callback)
    new CircuitBreaker(
      _stateRef = stateRef,
      _maxFailures = maxFailures,
      _resetTimeout = resetTimeout,
      _exponentialBackoffFactor = exponentialBackoffFactor,
      _maxResetTimeout = maxResetTimeout,
      onRejected = onRejected,
      onClosed = onClosed,
      onHalfOpen = onHalfOpen,
      onOpen = onOpen)
  }

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
  def doOnHalfOpen(callback: F[Unit]): CircuitBreaker[F] = {
    val onHalfOpen = this.onHalfOpen.flatMap(_ => callback)
    new CircuitBreaker(
      _stateRef = stateRef,
      _maxFailures = maxFailures,
      _resetTimeout = resetTimeout,
      _exponentialBackoffFactor = exponentialBackoffFactor,
      _maxResetTimeout = maxResetTimeout,
      onRejected = onRejected,
      onClosed = onClosed,
      onHalfOpen = onHalfOpen,
      onOpen = onOpen)
  }

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
  def doOnOpen(callback: F[Unit]): CircuitBreaker[F] = {
    val onOpen = this.onOpen.flatMap(_ => callback)
    new CircuitBreaker(
      _stateRef = stateRef,
      _maxFailures = maxFailures,
      _resetTimeout = resetTimeout,
      _exponentialBackoffFactor = exponentialBackoffFactor,
      _maxResetTimeout = maxResetTimeout,
      onRejected = onRejected,
      onClosed = onClosed,
      onHalfOpen = onHalfOpen,
      onOpen = onOpen)
  }
}

/**
  * @define maxFailuresParam is the maximum count for failures before
  *        opening the circuit breaker
  *
  * @define resetTimeoutParam is the timeout to wait in the `Open` state
  *        before attempting a close of the circuit breaker (but without
  *        the backoff factor applied)
  *
  * @define exponentialBackoffFactorParam is a factor to use for resetting
  *        the `resetTimeout` when in the `HalfOpen` state, in case
  *        the attempt to `Close` fails
  *
  * @define maxResetTimeoutParam is the maximum timeout the circuit breaker
  *        is allowed to use when applying the `exponentialBackoffFactor`
  *
  * @define onRejectedParam is a callback for signaling rejected tasks, so
  *         every time a task execution is attempted and rejected in
  *         [[CircuitBreaker.Open Open]] or [[CircuitBreaker.HalfOpen HalfOpen]]
  *         states
  *
  * @define onClosedParam is a callback for signaling transitions to
  *         the [[CircuitBreaker.Closed Closed]] state
  *
  * @define onHalfOpenParam is a callback for signaling transitions to
  *         [[CircuitBreaker.HalfOpen HalfOpen]]
  *
  * @define onOpenParam is a callback for signaling transitions to
  *         [[CircuitBreaker.Open Open]]
  *
  * @define paddingParam is the
  *        [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
  *        to apply to the underlying atomic reference used,
  *        to use in case contention and "false sharing" become a problem
  *
  * @define unsafeWarning '''UNSAFE WARNING:''' this builder is unsafe to use
  *         because creating a circuit breaker allocates shared mutable states,
  *         which violates referential transparency. Prefer to use the safe
  *         [[monix.catnap.CircuitBreaker.Builders.of CircuitBreaker[F].of]]
  *         and pass that `CircuitBreaker` around as a plain parameter.
  */
private[catnap] sealed trait CircuitBreakerDocs extends Any

object CircuitBreaker extends CircuitBreakerDocs {
  /**
    * Builders specified for [[CircuitBreaker]], using the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type technique]].
    *
    * Example:
    * {{{
    *   import scala.concurrent.duration._
    *   import cats.effect.{IO, Clock}
    *   implicit val clock = Clock.create[IO]
    *
    *   val cb = CircuitBreaker[IO].of(
    *     maxFailures = 10,
    *     resetTimeout = 3.second,
    *     exponentialBackoffFactor = 2
    *   )
    * }}}
    */
  def apply[F[_]](implicit F: Sync[F]): Builders[F] = new Builders[F](F)

  /** Safe builder.
    *
    * @see [[Builders.of CircuitBreaker[F].of]], the version that uses the
    *      "partially-applied type technique"
    *
    * @param maxFailures $maxFailuresParam
    * @param resetTimeout $resetTimeoutParam
    * @param exponentialBackoffFactor $exponentialBackoffFactorParam
    * @param maxResetTimeout $maxResetTimeoutParam
    * @param padding $paddingParam
    */
  def of[F[_]](
    maxFailures: Int,
    resetTimeout: FiniteDuration,
    exponentialBackoffFactor: Double = 1.0,
    maxResetTimeout: Duration = Duration.Inf,
    padding: PaddingStrategy = NoPadding)
    (implicit F: Sync[F], clock: Clock[F]): F[CircuitBreaker[F]] = {

    CircuitBreaker[F].of(
      maxFailures = maxFailures,
      resetTimeout = resetTimeout,
      exponentialBackoffFactor = exponentialBackoffFactor,
      maxResetTimeout = maxResetTimeout,
      padding = padding
    )
  }

  /** Unsafe builder (that violates referential transparency).
    *
    * $unsafeWarning
    *
    * @see [[Builders.unsafe CircuitBreaker[F].unsafe]], the version that
    *      uses the "partially-applied type technique"
    *
    * @param maxFailures $maxFailuresParam
    * @param resetTimeout $resetTimeoutParam
    * @param exponentialBackoffFactor $exponentialBackoffFactorParam
    * @param maxResetTimeout $maxResetTimeoutParam
    * @param padding $paddingParam
    */
  @UnsafeBecauseImpure
  def unsafe[F[_]](
    maxFailures: Int,
    resetTimeout: FiniteDuration,
    exponentialBackoffFactor: Double = 1.0,
    maxResetTimeout: Duration = Duration.Inf,
    padding: PaddingStrategy = NoPadding)
    (implicit F: Sync[F], clock: Clock[F]): CircuitBreaker[F] = {

    CircuitBreaker[F].unsafe(
      maxFailures = maxFailures,
      resetTimeout = resetTimeout,
      exponentialBackoffFactor = exponentialBackoffFactor,
      maxResetTimeout = maxResetTimeout,
      padding = padding
    )
  }

  /**
    * Builders specified for [[CircuitBreaker]], using the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type technique]].
    *
    */
  final class Builders[F[_]](val F: Sync[F]) extends AnyVal with CircuitBreakerDocs {
    /**
      * Safe builder for a [[CircuitBreaker]] reference.
      *
      * @param maxFailures $maxFailuresParam
      * @param resetTimeout $resetTimeoutParam
      * @param exponentialBackoffFactor $exponentialBackoffFactorParam
      * @param maxResetTimeout $maxResetTimeoutParam
      * @param onRejected $onRejectedParam
      * @param onClosed $onClosedParam
      * @param onHalfOpen $onHalfOpenParam
      * @param onOpen $onOpenParam
      * @param padding $paddingParam
      */
    def of(
      maxFailures: Int,
      resetTimeout: FiniteDuration,
      exponentialBackoffFactor: Double = 1.0,
      maxResetTimeout: Duration = Duration.Inf,
      onRejected: F[Unit] = F.unit,
      onClosed: F[Unit] = F.unit,
      onHalfOpen: F[Unit] = F.unit,
      onOpen: F[Unit] = F.unit,
      padding: PaddingStrategy = NoPadding)
      (implicit clock: Clock[F]): F[CircuitBreaker[F]] = {

      F.delay(unsafe(
        maxFailures = maxFailures,
        resetTimeout = resetTimeout,
        exponentialBackoffFactor = exponentialBackoffFactor,
        maxResetTimeout = maxResetTimeout,
        onRejected = onRejected,
        onClosed = onClosed,
        onHalfOpen = onHalfOpen,
        onOpen = onOpen,
        padding = padding
      ))
    }

    /** Unsafe builder, an alternative to [[of CircuitBreaker[F].of]] for
      * people knowing what they are doing.
      *
      * $unsafeWarning
      *
      * @param maxFailures $maxFailuresParam
      * @param resetTimeout $resetTimeoutParam
      * @param exponentialBackoffFactor $exponentialBackoffFactorParam
      * @param maxResetTimeout $maxResetTimeoutParam
      * @param onRejected $onRejectedParam
      * @param onClosed $onClosedParam
      * @param onHalfOpen $onHalfOpenParam
      * @param onOpen $onOpenParam
      * @param padding $paddingParam
      */
    @UnsafeBecauseImpure
    def unsafe(
      maxFailures: Int,
      resetTimeout: FiniteDuration,
      exponentialBackoffFactor: Double = 1.0,
      maxResetTimeout: Duration = Duration.Inf,
      onRejected: F[Unit] = F.unit,
      onClosed: F[Unit] = F.unit,
      onHalfOpen: F[Unit] = F.unit,
      onOpen: F[Unit] = F.unit,
      padding: PaddingStrategy = NoPadding)
      (implicit clock: Clock[F]): CircuitBreaker[F] = {

      val atomic = Atomic.withPadding(Closed(0) : State, padding)
      new CircuitBreaker[F](
        _stateRef = atomic,
        _maxFailures = maxFailures,
        _resetTimeout = resetTimeout,
        _exponentialBackoffFactor = exponentialBackoffFactor,
        _maxResetTimeout = maxResetTimeout,
        onRejected = onRejected,
        onClosed = onClosed,
        onHalfOpen = onHalfOpen,
        onOpen = onOpen)(F, clock)
    }
  }

  /** Type-alias to document timestamps specified in milliseconds, as returned by
    * [[monix.execution.Scheduler.clockRealTime Scheduler.clockRealTime]].
    */
  type Timestamp = Long

  /** An enumeration that models the internal state of [[CircuitBreaker]],
    * kept in an [[monix.execution.atomic.Atomic Atomic]] for synchronization.
    *
    * The initial state when initializing a [[CircuitBreaker]] is
    * [[Closed]]. The available states:
    *
    *  - [[Closed]] in case tasks are allowed to go through
    *  - [[Open]] in case the circuit breaker is active and rejects incoming tasks
    *  - [[HalfOpen]] in case a reset attempt was triggered and it is waiting for
    *    the result in order to evolve in [[Closed]], or back to [[Open]]
    */
  sealed abstract class State

  /** The initial [[State]] of the [[CircuitBreaker]]. While in this
    * state the circuit breaker allows tasks to be executed.
    *
    * Contract:
    *
    *  - Exceptions increment the `failures` counter
    *  - Successes reset the failure count to zero
    *  - When the `failures` counter reaches the `maxFailures` count,
    *    the breaker is tripped into the `Open` state
    *
    * @param failures is the current failures count
    */
  final case class Closed(failures: Int) extends State

  /** [[State]] of the [[CircuitBreaker]] in which the circuit
    * breaker rejects all tasks with an
    * [[monix.execution.exceptions.ExecutionRejectedException ExecutionRejectedException]].
    *
    * Contract:
    *
    *  - all tasks fail fast with `ExecutionRejectedException`
    *  - after the configured `resetTimeout`, the circuit breaker
    *    enters a [[HalfOpen]] state, allowing one task to go through
    *    for testing the connection
    *
    * @param startedAt is the timestamp in milliseconds since the
    *        epoch when the transition to `Open` happened
    *
    * @param resetTimeout is the current `resetTimeout` that is
    *        applied to this `Open` state, to be multiplied by the
    *        exponential backoff factor for the next transition from
    *        `HalfOpen` to `Open`, in case the reset attempt fails
    *
    * @param awaitClose is a `Deferred` (pure promise) that will get
    *        completed when the `CircuitBreaker` will switch to the
    *        `Closed` state again; this reference is `None` in case
    *        the `F[_]` used does not implement `cats.effect.Async`,
    *        because only with `Async` data types we can wait for
    *        completion.
    */
  final class Open private (
    val startedAt: Timestamp,
    val resetTimeout: FiniteDuration,
    private[catnap] val awaitClose: CancelablePromise[Unit]) extends State {

    /** The timestamp in milliseconds since the epoch, specifying
      * when the `Open` state is to transition to [[HalfOpen]].
      *
      * It is calculated as:
      * `startedAt + resetTimeout.toMillis`
      */
    val expiresAt: Timestamp = startedAt + resetTimeout.toMillis

    override def equals(other: Any): Boolean = other match {
      case that: Open =>
        startedAt == that.startedAt &&
        resetTimeout == that.resetTimeout &&
        awaitClose == that.awaitClose
      case _ =>
        false
    }

    override def hashCode(): Int = {
      val state: Seq[Any] = Seq(startedAt, resetTimeout, awaitClose)
      state.foldLeft(0)((a, b) => 31 * a + b.hashCode())
    }
  }

  object Open {
    /** Private builder. */
    private[catnap] def apply(startedAt: Timestamp, resetTimeout: FiniteDuration, awaitClose: CancelablePromise[Unit]): Open =
      new Open(startedAt, resetTimeout, awaitClose)

    /** Implements the pattern matching protocol. */
    def unapply(state: Open): Option[(Timestamp, FiniteDuration)] =
      Some((state.startedAt, state.resetTimeout))
  }

  /** [[State]] of the [[CircuitBreaker]] in which the circuit
    * breaker has already allowed a task to go through, as a reset
    * attempt, in order to test the connection.
    *
    * Contract:
    *
    *  - The first task when `Open` has expired is allowed through
    *    without failing fast, just before the circuit breaker is
    *    evolved into the `HalfOpen` state
    *  - All tasks attempted in `HalfOpen` fail-fast with an exception
    *    just as in [[Open]] state
    *  - If that task attempt succeeds, the breaker is reset back to
    *    the `Closed` state, with the `resetTimeout` and the
    *    `failures` count also reset to initial values
    *  - If the first call fails, the breaker is tripped again into
    *    the `Open` state (the `resetTimeout` is multiplied by the
    *    exponential backoff factor)
    *
    * @param resetTimeout is the current `resetTimeout` that was
    *        applied to the previous `Open` state, to be multiplied by
    *        the exponential backoff factor for the next transition to
    *        `Open`, in case the reset attempt fails
    *
    * @param awaitClose is a `Deferred` (pure promise) that will get
    *        completed when the `CircuitBreaker` will switch to the
    *        `Closed` state again; this reference is `None` in case
    *        the `F[_]` used does not implement `cats.effect.Async`,
    *        because only with `Async` data types we can wait for
    *        completion.
    */
  final class HalfOpen private (
    val resetTimeout: FiniteDuration,
    private[catnap] val awaitClose: CancelablePromise[Unit])
    extends State {

    override def equals(other: Any): Boolean = other match {
      case that: HalfOpen =>
        resetTimeout == that.resetTimeout &&
        awaitClose == that.awaitClose
      case _ =>
        false
    }

    override def hashCode(): Int = {
      val state: Seq[Object] = Seq(resetTimeout, awaitClose)
      state.foldLeft(0)((a, b) => 31 * a + b.hashCode())
    }
  }

  object HalfOpen {
    /** Private builder. */
    private[catnap] def apply(resetTimeout: FiniteDuration, awaitClose: CancelablePromise[Unit]): HalfOpen =
      new HalfOpen(resetTimeout, awaitClose)

    /** Implements the pattern matching protocol. */
    def unapply(state: HalfOpen): Option[FiniteDuration] =
      Some(state.resetTimeout)
  }
}
