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

package monix.execution.schedulers

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
  * recursive loops, as executed by the [[monix.execution.RunLoop]],
  * or to events pushed into consumers.
  */
sealed abstract class ExecutionModel {
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

  /** Specifies whether the first task in a batch should execute
    * asynchronously on a different logical thread, or not. This is desirable
    * sometimes in order to ensure that the current thread is never blocked.
    */
  val firstIsAsync: Boolean

  /** Always equal to `recommendedBatchSize-1`.
    *
    * Provided for convenience.
    */
  val batchedExecutionModulus: Int
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
    val recommendedBatchSize = math.roundToPowerOf2(Int.MaxValue) - 1
    val batchedExecutionModulus = recommendedBatchSize-1

    /** For the synchronous [[ExecutionModel]] the first task in a batch
      * shouldn't be asynchronous, therefore [[ExecutionModel.firstIsAsync]]
      * is always `false`.
      */
    val firstIsAsync = false
  }

  /** [[ExecutionModel]] that specifies a [[monix.execution.RunLoop RunLoop]]
    * should always do async execution of tasks, forking logical threads
    * on each step.
    */
  case object AlwaysAsyncExecution extends ExecutionModel {
    /** The [[ExecutionModel.recommendedBatchSize]] for the
      * [[SynchronousExecution]] type is set to one.
      */
    val recommendedBatchSize = 1
    val batchedExecutionModulus = 0

    /** For the asynchronous [[ExecutionModel]] the first task in a batch
      * should be asynchronous, therefore [[ExecutionModel.firstIsAsync]]
      * is always `true`.
      */
    val firstIsAsync = false
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
    * By specifying the [[ExecutionModel.recommendedBatchSize]] and
    * the [[ExecutionModel.firstIsAsync]] parameters, the configuration
    * can be fine-tuned.
    */
  final case class BatchedExecution(private val batchSize: Int, firstIsAsync: Boolean)
    extends ExecutionModel {
    val recommendedBatchSize = math.nextPowerOf2(batchSize)
    val batchedExecutionModulus = recommendedBatchSize-1
  }

  final val Default: ExecutionModel = {
    BatchedExecution(Platform.recommendedBatchSize, firstIsAsync = false)
  }
}