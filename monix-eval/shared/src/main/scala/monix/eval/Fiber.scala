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

import monix.eval.internal.TaskCancellation

/** `Fiber` represents the (pure) result of a [[Task]] being started concurrently
  * and that can be either joined or cancelled.
  *
  * You can think of fibers as being lightweight threads, a fiber being a
  * concurrency primitive for doing cooperative multi-tasking.
  *
  * For example a `Fiber` value is the result of evaluating [[Task.start]]:
  *
  * {{{
  *   val task = Task.evalAsync(println("Hello!"))
  *
  *   val forked: Task[Fiber[Unit]] = task.start
  * }}}
  *
  * Usage example:
  *
  * {{{
  *   val launchMissiles = Task(println("Missiles launched!"))
  *   val runToBunker = Task(println("Run Lola run!"))
  *
  *   for {
  *     fiber <- launchMissiles.start
  *     _ <- runToBunker.onErrorHandleWith { error =>
  *       // Retreat failed, cancel launch (maybe we should
  *       // have retreated to our bunker before the launch?)
  *       fiber.cancel.flatMap(_ => Task.raiseError(error))
  *     }
  *     aftermath <- fiber.join
  *   } yield {
  *     aftermath
  *   }
  * }}}
  */
trait Fiber[A] extends cats.effect.Fiber[Task, A] {
  /**
    * Triggers the cancellation of the fiber.
    *
    * Returns a new task that will complete when the cancellation is
    * sent (but not when it is observed or acted upon).
    *
    * Note that if the background process that's evaluating the result
    * of the underlying fiber is already complete, then there's nothing
    * to cancel.
    */
  def cancel: Task[Unit]

  /** Returns a new task that will await for the completion of the
    * underlying fiber, (asynchronously) blocking the current run-loop
    * until that result is available.
    */
  def join: Task[A]
}

object Fiber {
  /**
    * Wraps a [[Task]] value in a `Fiber` interface.
    *
    * This is usually done when the given `Task` reference is linked
    * to some mutable variable and thus something that's worth cancelling.
    */
  def apply[A](task: Task[A]): Fiber[A] =
    new Impl(task)

  private final class Impl[A](val join: Task[A]) extends Fiber[A] {
    def cancel: Task[Unit] =
      TaskCancellation.signal(join)
  }
}
