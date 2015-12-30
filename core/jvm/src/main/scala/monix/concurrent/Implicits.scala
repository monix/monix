/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.concurrent

import monix.concurrent.schedulers.AsyncScheduler
import scala.concurrent.ExecutionContext

/**
 * Defines implicit values that can be used by importing in the 
 * current context.
 * 
 * Example:
 * {{{
 *   import monix.concurrent.Implicits.globalScheduler
 * }}}
 */
object Implicits {
  /**
   * A global [[Scheduler]] instance, provided for convenience, piggy-backing
   * on top of Scala's own `concurrent.ExecutionContext.global`, which is a
   * `ForkJoinPool`.
   *
   * It can be tuned by setting the following JVM system properties:
   *
   * - "scala.concurrent.context.minThreads" an integer specifying the minimum
   *   number of active threads in the pool
   *
   * - "scala.concurrent.context.maxThreads" an integer specifying the maximum
   *   number of active threads in the pool
   *
   * - "scala.concurrent.context.numThreads" can be either an integer,
   *   specifying the parallelism directly or a string with the format "xNUM"
   *   (e.g. "x1.5") specifying the multiplication factor of the number of
   *   available processors (taken with `Runtime.availableProcessors`)
   *
   * The formula for calculating the parallelism in our pool is
   * `min(maxThreads, max(minThreads, numThreads))`.
   *
   * To set a system property from the command line, a JVM parameter must be
   * given to the `java` command as `-Dname=value`. So as an example, to customize
   * this global scheduler, we could start our process like this:
   *
   * <pre>
   *   java -Dscala.concurrent.context.minThreads=2 \
   *        -Dscala.concurrent.context.maxThreads=30 \
   *        -Dscala.concurrent.context.numThreads=x1.5 \
   *        ...
   * </pre>
   *
   * As a note, this being backed by Scala's own global execution context,
   * it is cooperating with Scala's BlockContext, so when operations marked
   * with `scala.concurrent.blocking` are encountered, the thread-pool may
   * decide to add extra threads in the pool. However this is not a thread-pool
   * that is optimal for doing blocking operations, so for example if you want
   * to do a lot of blocking I/O, then use a Scheduler backed by a
   * thread-pool that is more optimal for blocking. See for example
   * [[Scheduler.io]].
   */
  implicit lazy val globalScheduler: Scheduler =
    AsyncScheduler(
      Scheduler.defaultScheduledExecutor,
      ExecutionContext.Implicits.global,
      UncaughtExceptionReporter.LogExceptionsToStandardErr
    )
}
