/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.CancelToken

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
  def cancel: CancelToken[Task]

  /** Returns a new task that will await for the completion of the
    * underlying fiber, (asynchronously) blocking the current run-loop
    * until that result is available.
    */
  def join: Task[A]
}

object Fiber {
  /**
    * Builds a [[Fiber]] value out of a `task` and its cancelation token.
    */
  def apply[A](task: Task[A], cancel: CancelToken[Task]): Fiber[A] =
    new Tuple(task, cancel)

  private final case class Tuple[A](join: Task[A], cancel: CancelToken[Task]) extends Fiber[A]
}
