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

package monix.execution.internal

import monix.execution.UncaughtExceptionReporter
import monix.execution.exceptions.CompositeException
import monix.execution.schedulers.CanBlock
import scala.concurrent.Awaitable
import scala.concurrent.duration.Duration
import scala.scalajs.js
import scala.util.control.NonFatal
import scala.annotation.unused

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

  /**
    * Reads environment variable in a platform-specific way.
    */
  def getEnv(key: String): Option[String] = {
    import js.Dynamic.global
    try {
      // Node.js specific API, could fail
      if (js.typeOf(global.process) == "object" && js.typeOf(global.process.env) == "object")
        global.process.env.selectDynamic(key).asInstanceOf[js.UndefOr[String]]
          .toOption
          .collect { case s: String => s.trim }
          .filter(_.nonEmpty)
      else
        None
    } catch {
      case NonFatal(_) => None
    }
  }

  /** Recommended batch size used for breaking synchronous loops in
    * asynchronous batches. When streaming value from a producer to
    * a synchronous consumer it's recommended to break the streaming
    * in batches as to not hold the current thread or run-loop
    * indefinitely.
    *
    * It's always a power of 2, because then for
    * applying the modulo operation we can just do:
    *
    * {{{
    *   val modulus = Platform.recommendedBatchSize - 1
    *   // ...
    *   nr = (nr + 1) & modulus
    * }}}
    */
  final val recommendedBatchSize: Int = 512

  /** Recommended chunk size in unbounded buffer implementations that are chunked,
    * or in chunked streams.
    *
    * Should be a power of 2.
    */
  final val recommendedBufferChunkSize: Int = 128

  /**
    * Auto cancelable run loops are set to `false` if Monix
    * is running on top of Scala.js.
    */
  final val autoCancelableRunLoops: Boolean = true

  /**
    * Local context propagation is set to `false` if Monix
    * is running on top of Scala.js given that it is single
    * threaded.
    */
  final val localContextPropagation: Boolean = false

  /** Blocks for the result of `fa`.
    *
    * This operation is only supported on top of the JVM, whereas for
    * JavaScript a dummy is provided.
    */
  def await[A](@unused fa: Awaitable[A], @unused timeout: Duration)(implicit @unused permit: CanBlock): A =
    throw new UnsupportedOperationException(
      "Blocking operations are not supported on top of JavaScript"
    )

  /** Composes multiple errors together, meant for those cases in which
    * error suppression, due to a second error being triggered, is not
    * acceptable.
    *
    * On top of the JVM this function uses `Throwable#addSuppressed`,
    * available since Java 7. On top of JavaScript the function would return
    * a `CompositeException`.
    */
  def composeErrors(first: Throwable, rest: Throwable*): Throwable =
    rest.filter(_ ne first).toList match {
      case Nil => first
      case nonEmpty =>
        first match {
          case CompositeException(errors) =>
            CompositeException(errors ::: nonEmpty)
          case _ =>
            CompositeException(first :: nonEmpty)
        }
    }

  /**
    * Useful utility that combines an `Either` result, which is what
    * `MonadError#attempt` returns.
    */
  def composeErrors(first: Throwable, second: Either[Throwable, _]): Throwable =
    second match {
      case Left(e2) => composeErrors(first, e2)
      case _ => first
    }

  /**
    * Returns the current thread's ID.
    *
    * To be used for multi-threading optimizations. Note that
    * in JavaScript this always returns the same value.
    */
  def currentThreadId(): Long = 1L

  /**
    * For reporting errors when we don't have access to
    * an error handler.
    */
  def reportFailure(e: Throwable): Unit = {
    UncaughtExceptionReporter.default.reportFailure(e)
  }
}
