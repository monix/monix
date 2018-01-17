/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval

import monix.execution.Scheduler
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.{Atomic, AtomicAny, PaddingStrategy}
import monix.execution.exceptions.ExecutionRejectedException

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/** The `TaskCircuitBreaker` is used to provide stability and prevent
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
  *  1. [[TaskCircuitBreaker$.Closed Closed]]: During normal
  *     operations or when the `TaskCircuitBreaker` starts
  *    - Exceptions increment the `failures` counter
  *    - Successes reset the failure count to zero
  *    - When the `failures` counter reaches the `maxFailures` count,
  *      the breaker is tripped into `Open` state
  *
  *  1. [[TaskCircuitBreaker$.Open Open]]: The circuit breaker rejects
  *     all tasks with an
  *     [[monix.execution.exceptions.ExecutionRejectedException ExecutionRejectedException]]
  *    - all tasks fail fast with `ExecutionRejectedException`
  *    - after the configured `resetTimeout`, the circuit breaker
  *      enters a [[TaskCircuitBreaker$.HalfOpen HalfOpen]] state,
  *      allowing one task to go through for testing the connection
  *
  *  1. [[TaskCircuitBreaker$.HalfOpen HalfOpen]]: The circuit breaker
  *     has already allowed a task to go through, as a reset attempt,
  *     in order to test the connection
  *    - The first task when `Open` has expired is allowed through
  *      without failing fast, just before the circuit breaker is
  *      evolved into the `HalfOpen` state
  *    - All tasks attempted in `HalfOpen` fail-fast with an exception
  *      just as in [[TaskCircuitBreaker$.Open Open]] state
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
  *   import monix.eval._
  *   import scala.concurrent.duration._
  *
  *   val circuitBreaker = TaskCircuitBreaker(
  *     maxFailures = 5,
  *     resetTimeout = 10.seconds
  *   )
  *
  *   //...
  *   val problematic = Task {
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
  *   val circuitBreaker = TaskCircuitBreaker(
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
  * =Credits=
  *
  * This Monix data type was inspired by the availability of
  * [[http://doc.akka.io/docs/akka/current/common/circuitbreaker.html Akka's Circuit Breaker]].
  */
final class TaskCircuitBreaker private (
  _stateRef: AtomicAny[TaskCircuitBreaker.State],
  _maxFailures: Int,
  _resetTimeout: FiniteDuration,
  _exponentialBackoffFactor: Double,
  _maxResetTimeout: Duration,
  onRejected: Task[Unit],
  onClosed: Task[Unit],
  onHalfOpen: Task[Unit],
  onOpen: Task[Unit]) {

  require(_maxFailures >= 0, "maxFailures >= 0")
  require(_exponentialBackoffFactor >= 1, "exponentialBackoffFactor >= 1")
  require(_resetTimeout > Duration.Zero, "resetTimeout > 0")
  require(_maxResetTimeout > Duration.Zero, "maxResetTimeout > 0")

  import monix.eval.TaskCircuitBreaker._
  private[this] val stateRef = _stateRef

  /** The maximum count for allowed failures before
    * opening the circuit breaker.
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

  /** Returns the current [[TaskCircuitBreaker.State]], meant for
    * debugging purposes.
    */
  def state: TaskCircuitBreaker.State =
    stateRef.get

  /** Function for counting failures in the `Closed` state,
    * triggering the `Open` state if necessary.
    */
  private[this] val maybeMarkOrResetFailures: (Option[Throwable] => Task[Unit]) =
    exOpt => Task.unsafeCreate[Unit] { (context, callback) =>
      // Recursive function because of going into CAS loop
      @tailrec def markFailure(s: Scheduler): Task[Unit] =
        stateRef.get match {
          case current @ Closed(failures) =>
            exOpt match {
              case None =>
                // In case of success, must reset the failures counter!
                if (failures == 0) Task.unit else {
                  val update = Closed(0)
                  if (!stateRef.compareAndSet(current, update))
                    markFailure(s) // retry?
                  else
                    Task.unit
                }

              case Some(ex) =>
                // In case of failure, we either increment the failures counter,
                // or we transition in the `Open` state.
                if (failures+1 < maxFailures) {
                  // It's fine, just increment the failures count
                  val update = Closed(failures+1)
                  if (!stateRef.compareAndSet(current, update))
                    markFailure(s) // retry?
                  else
                    Task.unit
                }
                else {
                  // We've gone over the permitted failures threshold,
                  // so we need to open the circuit breaker
                  val update = Open(s.currentTimeMillis(), resetTimeout)

                  if (!stateRef.compareAndSet(current, update))
                    markFailure(s) // retry?
                  else
                    onOpen
                }
            }

          case Open(_,_) | HalfOpen(_) =>
            // Concurrent execution of another handler happened, we are
            // already in an Open state, so not doing anything extra
            Task.unit
        }

      val s = context.scheduler
      s.executeTrampolined { () =>
        val task = markFailure(s)
        Task.unsafeStartNow(task, context, callback)
      }
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
  private def attemptReset[A](task: Task[A], resetTimeout: FiniteDuration)(implicit s: Scheduler): Task[A] =
    onHalfOpen.flatMap(_ => task).materialize.flatMap {
      case Success(value) =>
        // While in HalfOpen only a reset attempt is allowed to update
        // the state, so setting this directly is safe
        stateRef.set(Closed(0))
        onClosed.map(_ => value)

      case Failure(ex) =>
        // Failed reset, which means we go back in the Open state with new expiry
        val nextTimeout = {
          val value = (resetTimeout.toMillis * exponentialBackoffFactor).millis
          if (maxResetTimeout.isFinite() && value > maxResetTimeout)
            maxResetTimeout.asInstanceOf[FiniteDuration]
          else
            value
        }

        stateRef.set(Open(s.currentTimeMillis(), nextTimeout))
        onOpen.flatMap(_ => Task.raiseError(ex))
    }

  /** Returns a new [[Task]] that upon execution will execute the given
    * task, but with the protection of this circuit breaker.
    */
  def protect[A](task: Task[A]): Task[A] = {
    @tailrec def execute()(implicit s: Scheduler): Task[A] =
      stateRef.get match {
        case Closed(_) =>
          // CircuitBreaker is closed, allowing our task to go through, but with an
          // attached error handler that transitions the state to Open if needed
          task.doOnFinish(maybeMarkOrResetFailures)

        case HalfOpen(_) =>
          // CircuitBreaker is in HalfOpen state, which means we still reject all tasks,
          // while waiting to see if our reset attempt succeeds or fails
          onRejected.flatMap { _ =>
            Task.raiseError(ExecutionRejectedException(
              "Rejected because the TaskCircuitBreaker is in the HalfOpen state"
            ))
          }

        case current @ Open(_, timeout) =>
          val now = s.currentTimeMillis()
          val expiresAt = current.expiresAt

          if (now >= expiresAt) {
            // The Open state has expired, so we are letting just one
            // task to execute, while transitioning into HalfOpen
            if (!stateRef.compareAndSet(current, HalfOpen(timeout)))
              execute() // retry!
            else
              attemptReset(task, timeout)
          }
          else {
            // Open isn't expired, so we need to fail
            val expiresInMillis = expiresAt - now
            onRejected.flatMap { _ =>
              Task.raiseError(ExecutionRejectedException(
                "Rejected because the TaskCircuitBreaker is in the Open state, " +
                s"attempting to close in $expiresInMillis millis"
              ))
            }
          }
      }

    Task.unsafeCreate { (context, callback) =>
      val s = context.scheduler
      s.executeTrampolined { () =>
        val loop = execute()(s)
        Task.unsafeStartNow(loop, context, callback)
      }
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
  def doOnRejectedTask(callback: Task[Unit]): TaskCircuitBreaker = {
    val onRejected = this.onRejected.flatMap(_ => callback)
    new TaskCircuitBreaker(
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
    * transitioning to the [[TaskCircuitBreaker.Closed Closed]] state.
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
  def doOnClosed(callback: Task[Unit]): TaskCircuitBreaker = {
    val onClosed = this.onClosed.flatMap(_ => callback)
    new TaskCircuitBreaker(
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
    * transitioning to the [[TaskCircuitBreaker.HalfOpen HalfOpen]]
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
  def doOnHalfOpen(callback: Task[Unit]): TaskCircuitBreaker = {
    val onHalfOpen = this.onHalfOpen.flatMap(_ => callback)
    new TaskCircuitBreaker(
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
    * transitioning to the [[TaskCircuitBreaker.Open Open]] state.
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
  def doOnOpen(callback: Task[Unit]): TaskCircuitBreaker = {
    val onOpen = this.onOpen.flatMap(_ => callback)
    new TaskCircuitBreaker(
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

object TaskCircuitBreaker {
  /** Builder for a [[TaskCircuitBreaker]] reference.
    *
    * [[Task]] returned by this operation produces a new
    * [[TaskCircuitBreaker]] each time it is evaluated. To share a state between
    * multiple consumers, pass [[TaskCircuitBreaker]] as a parameter or use [[Task.memoize]]
    *
    * @param maxFailures is the maximum count for failures before
    *        opening the circuit breaker
    * @param resetTimeout is the timeout to wait in the `Open` state
    *        before attempting a close of the circuit breaker (but
    *        without the backoff factor applied)
    * @param exponentialBackoffFactor is a factor to use for resetting
    *        the `resetTimeout` when in the `HalfOpen` state, in case
    *        the attempt to `Close` fails
    * @param maxResetTimeout is the maximum timeout the circuit breaker
    *        is allowed to use when applying the `exponentialBackoffFactor`
    *
    * @param onRejected is for signaling rejected tasks
    * @param onClosed is for signaling a transition to `Closed`
    * @param onHalfOpen is for signaling a transition to `HalfOpen`
    * @param onOpen is for signaling a transition to `Open`
    * @param padding is the
    *        [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    *        to apply to the underlying atomic reference used,
    *        to use in case contention and "false sharing" become a problem
    */
  def apply(
    maxFailures: Int,
    resetTimeout: FiniteDuration,
    exponentialBackoffFactor: Double = 1.0,
    maxResetTimeout: Duration = Duration.Inf,
    onRejected: Task[Unit] = Task.unit,
    onClosed: Task[Unit] = Task.unit,
    onHalfOpen: Task[Unit] = Task.unit,
    onOpen: Task[Unit] = Task.unit,
    padding: PaddingStrategy = NoPadding): Task[TaskCircuitBreaker] = Task.eval {

    val atomic = Atomic.withPadding(Closed(0) : State, padding)
    new TaskCircuitBreaker(
      _stateRef = atomic,
      _maxFailures = maxFailures,
      _resetTimeout = resetTimeout,
      _exponentialBackoffFactor = exponentialBackoffFactor,
      _maxResetTimeout = maxResetTimeout,
      onRejected = onRejected,
      onClosed = onClosed,
      onHalfOpen = onHalfOpen,
      onOpen = onOpen
    )
  }

  /** Type-alias to document timestamps specified in milliseconds, as returned by
    * [[monix.execution.Scheduler.currentTimeMillis Scheduler.currentTimeMillis]].
    */
  type Timestamp = Long

  /** An enumeration that models the internal state of [[TaskCircuitBreaker]],
    * kept in an [[monix.execution.atomic.Atomic Atomic]] for synchronization.
    *
    * The initial state when initializing a [[TaskCircuitBreaker]] is
    * [[Closed]]. The available states:
    *
    *  - [[Closed]] in case tasks are allowed to go through
    *  - [[Open]] in case the circuit breaker is active and rejects incoming tasks
    *  - [[HalfOpen]] in case a reset attempt was triggered and it is waiting for
    *    the result in order to evolve in [[Closed]], or back to [[Open]]
    */
  sealed abstract class State

  /** The initial [[State]] of the [[TaskCircuitBreaker]]. While in this
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

  /** [[State]] of the [[TaskCircuitBreaker]] in which the circuit
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
    * @param resetTimeout is the current `resetTimeout` that is
    *        applied to this `Open` state, to be multiplied by the
    *        exponential backoff factor for the next transition from
    *        `HalfOpen` to `Open`, in case the reset attempt fails
    */
  final case class Open(startedAt: Timestamp, resetTimeout: FiniteDuration) extends State {
    /** The timestamp in milliseconds since the epoch, specifying
      * when the `Open` state is to transition to [[HalfOpen]].
      *
      * It is calculated as:
      * {{{
      *   startedAt + resetTimeout.toMillis
      * }}}
      */
    val expiresAt: Timestamp = startedAt + resetTimeout.toMillis
  }

  /** [[State]] of the [[TaskCircuitBreaker]] in which the circuit
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
    */
  final case class HalfOpen(resetTimeout: FiniteDuration) extends State
}
