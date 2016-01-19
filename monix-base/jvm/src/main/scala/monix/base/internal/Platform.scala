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
 *
 */

package monix.base.internal

import scala.util.Try

private[monix] object Platform {
  /**
    * Returns `true` in case Monix is running on top of Scala.js,
    * or `false` otherwise.
    */
  final val isJS = false

  /**
    * Returns `true` in case Monix is running on top of the JVM,
    * or `false` otherwise.
    */
  final val isJVM = true

  /** Recommended batch size used for breaking synchronous loops in
    * asynchronous batches. When streaming value from a producer to
    * a synchronous consumer it's recommended to break the streaming
    * in batches as to not hold the current thread or run-loop
    * indefinitely.
    *
    * Rounding up to the closest power of 2, because then for
    * applying the modulo operation we can just do:
    * {{{
    *   val modulus = Platform.recommendedBatchSize - 1
    *   // ...
    *   nr = (nr + 1) & modulus
    * }}}
    *
    * Can be configured by setting Java properties:
    *
    * <pre>
    *   java -Dmonix.environment.batchSize=256 \
    *        ...
    * </pre>
    */
  final val recommendedBatchSize: Int = {
    Option(System.getProperty("monix.environment.batchSize", ""))
      .filter(s => s != null && s.nonEmpty)
      .flatMap(s => Try(s.toInt).toOption)
      .map(math.nextPowerOf2)
      .getOrElse(1024)
  }
}
