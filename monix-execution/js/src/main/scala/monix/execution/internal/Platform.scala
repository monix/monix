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

package monix.execution.internal

import monix.execution.schedulers.CanBlock
import scala.concurrent.Awaitable
import scala.concurrent.duration.Duration

private[monix] object Platform {
  /**
    * Returns `true` in case Monix is running on top of Scala.js,
    * or `false` otherwise.
    */
  final val isJS = true

  /**
    * Returns `true` in case Monix is running on top of the JVM,
    * or `false` otherwise.
    */
  final val isJVM = false

  /** Recommended batch size used for breaking synchronous loops in
    * asynchronous batches. When streaming value from a producer to
    * a synchronous consumer it's recommended to break the streaming
    * in batches as to not hold the current thread or run-loop
    * indefinitely.
    *
    * It's always a power of 2, because then for
    * applying the modulo operation we can just do:
    * {{{
    *   val modulus = Platform.recommendedBatchSize - 1
    *   // ...
    *   nr = (nr + 1) & modulus
    * }}}
    */
  final val recommendedBatchSize: Int = 512

  /**
    * Auto cancelable run loops are set to `false` if Monix
    * is running on top of Scala.js.
    */
  final val autoCancelableRunLoops: Boolean = false

  /**
    * Local context propagation is set to `false` if Monix
    * is running on top of Scala.js given that it is single
    * threaded.
    */
  final val localContextPropagation: Boolean = false

  /**
    * Establishes the maximum stack depth for fused `.map` operations
    * for JavaScript.
    *
    * The default for JavaScript is 32, from which we subtract 1
    * as an optimization.
    */
  final val fusionMaxStackDepth = 31

  /** Blocks for the result of `fa`.
    *
    * This operation is only supported on top of the JVM, whereas for
    * JavaScript a dummy is provided.
    */
  def await[A](fa: Awaitable[A], timeout: Duration)(implicit permit: CanBlock): A =
    throw new UnsupportedOperationException(
      "Blocking operations are not supported on top of JavaScript"
    )
}
