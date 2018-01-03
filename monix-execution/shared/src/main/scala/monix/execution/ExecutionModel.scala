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

package monix.execution

import monix.execution.internal.math
import monix.execution.internal.Platform

/** Specification for run-loops, imposed by the `Scheduler`.
  *
  * When executing tasks, a run-loop can always execute tasks
  * asynchronously (by forking logical threads), or it can always
  * execute them synchronously (same thread and call-stack, by
  * using an internal trampoline), or it can do a mixed mode
  * that executes tasks in batches before forking.
  *
  * The specification is considered a recommendation for how
  * run loops should behave, but ultimately it's up to the client
  * to choose the best execution model. This can be related to
  * recursive loops or to events pushed into consumers.
  */
sealed abstract class ExecutionModel extends Product with Serializable {
  /** Recommended batch size used for breaking synchronous loops in
    * asynchronous batches. When streaming value from a producer to
    * a synchronous consumer it's recommended to break the streaming
    * in batches as to not hold the current thread or run-loop
    * indefinitely.
    *
    * This is rounded to the next power of 2, because then for
    * applying the modulo operation we can just do:
    *
    * {{{
    *   val modulus = recommendedBatchSize - 1
    *   // ...
    *   nr = (nr + 1) & modulus
    * }}}
    *
    * For the JVM the default value can be adjusted with:
    *
    * <pre>
    *   java -Dmonix.environment.batchSize=2048 \
    *        ...
    * </pre>
    */
  val recommendedBatchSize: Int

  /** Always equal to `recommendedBatchSize-1`.
    *
    * Provided for convenience.
    */
  val batchedExecutionModulus: Int

  /** Returns the next frame index in the run-loop.
    *
    * If the returned integer is zero, then the next
    * cycle in the run-loop should execute asynchronously.
    */
  def nextFrameIndex(current: Int): Int
}

object ExecutionModel {
  /** [[ExecutionModel]] specifying that execution should be
    * synchronous (immediate, trampolined) for as long as possible.
    */
  case object SynchronousExecution extends ExecutionModel {
    /** The [[ExecutionModel.recommendedBatchSize]] for the
      * [[SynchronousExecution]] type is set to the maximum power
      * of 2 expressible with a `Int`, which is 2^30^ (or 1,073,741,824).
      */
    val recommendedBatchSize = math.roundToPowerOf2(Int.MaxValue)
    val batchedExecutionModulus = recommendedBatchSize-1

    /** Returns the next frame index in the run-loop.
      *
      * For `SynchronousExecution` this function always returns
      * a positive constant.
      */
    def nextFrameIndex(current: Int): Int = 1
  }

  /** [[ExecutionModel]] that specifies a run-loop should always do
    * async execution of tasks, forking logical threads
    * on each step.
    */
  case object AlwaysAsyncExecution extends ExecutionModel {
    /** The [[ExecutionModel.recommendedBatchSize]] for the
      * [[SynchronousExecution]] type is set to one.
      */
    val recommendedBatchSize = 1
    val batchedExecutionModulus = 0

    /** Returns the next frame index in the run-loop.
      *
      * For `AlwaysAsyncExecution` this function always returns
      * zero, signaling that the next cycle in the run-loop
      * should always be async.
      */
    def nextFrameIndex(current: Int): Int = 0
  }

  /** [[ExecutionModel]] specifying an mixed execution mode under
    * which tasks are executed synchronously in batches up
    * to a maximum size.
    *
    * After a batch of tasks of the
    * [[ExecutionModel.recommendedBatchSize recommended size]] is
    * executed, the next execution should be asynchronous,
    * forked on a different logical thread.
    *
    * By specifying the [[ExecutionModel.recommendedBatchSize]],
    * the configuration can be fine-tuned.
    */
  final case class BatchedExecution(
    private val batchSize: Int)
    extends ExecutionModel {

    val recommendedBatchSize = math.nextPowerOf2(batchSize)
    val batchedExecutionModulus = recommendedBatchSize-1

    def nextFrameIndex(current: Int): Int =
      (current + 1) & batchedExecutionModulus
  }

  /** Extension methods for [[ExecutionModel]]. */
  implicit final class Extensions(val self: ExecutionModel) extends AnyVal {
    /** Returns `true` if this execution model is
      * [[AlwaysAsyncExecution]] or `false` otherwise.
      */
    def isAlwaysAsync: Boolean =
      self match {
        case AlwaysAsyncExecution => true
        case _ => false
      }

    /** Returns `true` if this execution model is
      * [[SynchronousExecution]] or `false` otherwise.
      */
    def isSynchronous: Boolean =
      self match {
        case SynchronousExecution => true
        case _ => false
      }

    /** Returns `true` if this execution model is
      * [[BatchedExecution]] or `false` otherwise.
      */
    def isBatched: Boolean =
      self match {
        case BatchedExecution(_) => true
        case _ => false
      }
  }

  final val Default: ExecutionModel =
    BatchedExecution(Platform.recommendedBatchSize)
}
