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

import monix.execution.schedulers.CanBlock
import scala.concurrent.{ Await, Awaitable }
import scala.concurrent.duration.Duration
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

  /**
    * Reads environment variable in a platform-specific way.
    */
  def getEnv(key: String): Option[String] =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)

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
  val recommendedBatchSize: Int = {
    Option(System.getProperty("monix.environment.batchSize", ""))
      .filter(s => s != null && s.nonEmpty)
      .flatMap(s => Try(s.toInt).toOption)
      .map(math.nextPowerOf2)
      .getOrElse(1024)
  }

  /** Recommended chunk size in unbounded buffer implementations that are chunked,
    * or in chunked streaming.
    *
    * Examples:
    *
    *  - the default when no `chunkSizeHint` is specified in
    *    [[monix.execution.BufferCapacity.Unbounded BufferCapacity.Unbounded]]
    *  - the chunk size used in
    *    [[monix.reactive.OverflowStrategy.Unbounded OverflowStrategy.Unbounded]]
    *  - the default in
    *    [[monix.tail.Iterant.fromConsumer Iterant.fromConsumer]] or in
    *    [[monix.tail.Iterant.fromConsumer Iterant.fromChannel]]
    *
    * Can be configured by setting Java properties:
    *
    * <pre>
    *   java -Dmonix.environment.bufferChunkSize=128 \
    *        ...
    * </pre>
    *
    * Should be a power of 2 or it gets rounded to one.
    */
  val recommendedBufferChunkSize: Int = {
    Option(System.getProperty("monix.environment.bufferChunkSize", ""))
      .filter(s => s != null && s.nonEmpty)
      .flatMap(s => Try(s.toInt).toOption)
      .map(math.nextPowerOf2)
      .getOrElse(256)
  }

  /** Default value for auto cancelable loops, set to `false`.
    *
    * On top of the JVM the default can be overridden by setting the following
    * system property:
    *
    * `monix.environment.autoCancelableRunLoops`
    *
    * You can set the following values:
    *
    *  - `true`, `yes` or `1` for enabling (the default)
    *  - `no`, `false` or `0` for disabling
    *
    * NOTE: this values was `false` by default prior to the Monix 3.0.0
    * release. This changed along with the release of Cats-Effect 1.1.0
    * which now recommends for this default to be `true` due to the design
    * of its type classes.
    */
  val autoCancelableRunLoops: Boolean = {
    Option(System.getProperty("monix.environment.autoCancelableRunLoops", ""))
      .map(_.toLowerCase)
      .forall(v => v != "no" && v != "false" && v != "0")
  }

  /**
    * Default value for local context propagation loops is set to
    * false. On top of the JVM the default can be overridden by
    * setting the following system property:
    *
    *  - `monix.environment.localContextPropagation`
    *    (`true`, `yes` or `1` for enabling)
    */
  val localContextPropagation: Boolean =
    Option(System.getProperty("monix.environment.localContextPropagation", ""))
      .map(_.toLowerCase)
      .exists(v => v == "yes" || v == "true" || v == "1")

  /** Blocks for the result of `fa`.
    *
    * This operation is only supported on top of the JVM, whereas for
    * JavaScript a dummy is provided.
    */
  def await[A](fa: Awaitable[A], timeout: Duration)(implicit permit: CanBlock): A =
    Await.result(fa, timeout)

  /** Composes multiple errors together, meant for those cases in which
    * error suppression, due to a second error being triggered, is not
    * acceptable.
    *
    * On top of the JVM this function uses `Throwable#addSuppressed`,
    * available since Java 7. On top of JavaScript the function would return
    * a `CompositeException`.
    */
  def composeErrors(first: Throwable, rest: Throwable*): Throwable = {
    for (e <- rest; if e ne first) first.addSuppressed(e)
    first
  }

  /** Useful utility that combines an `Either` result, which is what
    * `MonadError#attempt` returns.
    */
  def composeErrors(first: Throwable, second: Either[Throwable, _]): Throwable =
    second match {
      case Left(e2) if first ne e2 =>
        first.addSuppressed(e2)
        first
      case _ =>
        first
    }

  /**
    * Returns the current thread's ID.
    *
    * To be used for multi-threading optimizations. Note that
    * in JavaScript this always returns the same value.
    */
  def currentThreadId(): Long = {
    Thread.currentThread().getId
  }

  /**
    * For reporting errors when we don't have access to
    * an error handler.
    */
  def reportFailure(e: Throwable): Unit = {
    val t = Thread.currentThread()
    t.getUncaughtExceptionHandler match {
      case null => DefaultUncaughtExceptionReporter.reportFailure(e)
      case ref => ref.uncaughtException(t, e)
    }
  }
}
