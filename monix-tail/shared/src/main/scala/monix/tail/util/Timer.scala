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

package monix.tail.util

import cats.effect.Async
import monix.eval.Task
import monix.execution.Scheduler
import scala.concurrent.duration.FiniteDuration

/** `Timer` is a pure [[monix.execution.Scheduler Scheduler]].
  *
  * Useful for scheduling execution with a delay and for
  * measuring time.
  *
  * It is not a type-class, as it does not have the coherence
  * requirement.
  */
trait Timer[F[_]] {

  /** Returns the current time in milliseconds, suspended in `F[_]`, similar to
    * [[monix.execution.Scheduler.currentTimeMillis Scheduler.currentTimeMillis]].
    *
    * Note that while the unit of time of the return value is a millisecond,
    * the granularity of the value depends on the underlying operating
    * system and may be larger. For example, some operating systems
    * measure time in units of tens of milliseconds.
    */
  def currentTimeMillis: F[Long]

  /** Asynchronous boundary described as an effectful `F[_]` that
    * can be used in `flatMap` chains to "shift" the continuation
    * of the run-loop to another thread or call stack.
    *
    * This is the `Async.shift` operation, without the need for an
    * implicit `ExecutionContext`.
    */
  def shift: F[Unit]

  /** Creates a new task that will sleep for the given duration,
    * emitting a tick when that time span is over.
    *
    * As an example on evaluation this will print "Hello!" after
    * 3 seconds:
    *
    * {{{
    *   import scala.concurrent.duration._
    *
    *   timer.sleep(3.seconds).flatMap { _ =>
    *     F.delay(println("Hello!"))
    *   }
    * }}}
    *
    * This is the polymorphic
    * [[monix.eval.Task.sleep Task.sleep]].
    */
  def sleep(timespan: FiniteDuration): F[Unit]

  /** Defers the creation of a task by using the provided
    * function, which has the ability to inject the current
    * time, being equivalent with:
    *
    * {{{
    *   timer.currentTimeMillis.flatMap(f)
    * }}}
    */
  def suspendTimed[A](f: Long => F[A]): F[A]
}

object Timer extends TimerImplicits0 {
  /** Retrieves the implicit `Timer[F]` available in the current scope.
    *
    * For example:
    * {{{
    *   Timer[Task].sleep(1.second)
    *   // ... is equivalent with ...
    *   implicitly[Timer[Task]].sleep(1.second)
    * }}}
    */
  def apply[F[_]](implicit timer: Timer[F]): Timer[F] =
    timer

  /** Implicit [[Timer]] available for [[monix.eval.Task Task]].
    *
    * Does not need any `Scheduler` available in the local context,
    * because `Task` has it embedded within its run-loop (to be injected
    * via `runAsync`).
    */
  implicit val forTask: Timer[Task] =
    new Timer[Task] {
      val shift: Task[Unit] =
        Task.shift
      val currentTimeMillis: Task[Long] =
        Task.deferAction(sc => Task.now(sc.currentTimeMillis()))
      def sleep(timespan: FiniteDuration): Task[Unit] =
        Task.sleep(timespan)
      override def suspendTimed[A](f: Long => Task[A]): Task[A] =
        Task.deferAction(sc => f(sc.currentTimeMillis()))
    }
}

private[util] abstract class TimerImplicits0 {
  /** Implicit [[Timer]] built for any `cats.effect.Async`
    * data type.
    *
    * Needs an implicit [[monix.execution.Scheduler Scheduler]]
    * available in scope.
    */
  implicit def forAsync[F[_]](implicit F: Async[F], sc: Scheduler): Timer[F] =
    new Timer[F] {
      val shift: F[Unit] = F.shift(sc)
      val currentTimeMillis: F[Long] =
        F.delay(sc.currentTimeMillis())
      def sleep(timespan: FiniteDuration): F[Unit] =
        F.async { cb =>
          sc.scheduleOnce(timespan.length, timespan.unit,
            new Runnable { def run(): Unit = cb(Right(())) })
        }
      override def suspendTimed[A](f: Long => F[A]): F[A] =
        F.suspend(f(sc.currentTimeMillis()))
    }
}
