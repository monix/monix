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

package monix.java8

import java.util.concurrent.{CancellationException, CompletableFuture, CompletionException}

import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}

import scala.util.{Failure, Success}

/** Utilities for integration with Java 8 classes
  *
  * Adds methods to construct a [[monix.eval.Task Task]] from
  * `java.util.concurrent.CompletableFuture`
  */
package object eval {
  implicit class TaskCompanionUtils(val source: Task.type) extends AnyVal {

    /** Converts the given Java `CompletableFuture` into a `Task`.
      *
      * NOTE: if you want to defer the creation of the future, use
      * in combination with [[Task.defer]]
      */
    def fromCompletableFuture[A](cf: CompletableFuture[A]): Task[A] =
      Task.async((_, cb) => {
        cf.handle[Unit]((result, err) =>
          err match {
            case null =>
              cb(Success(result))
            case _: CancellationException =>
              ()
            case ex: CompletionException if ex.getCause ne null =>
              cb(Failure(ex.getCause))
            case ex =>
              cb(Failure(ex))
        })
        Cancelable(() => cf.cancel(true))
      })

    /** Wraps calls that generate `CompletableFuture` results
      * into a [[Task]], provided a callback with an injected
      * [[monix.execution.Scheduler Scheduler]] to act as the
      * `Executor` for asynchronous actions.
      */
    def deferCompletableFutureAction[A](f: Scheduler => CompletableFuture[A]): Task[A] =
      Task.deferAction { sc =>
        fromCompletableFuture(f(sc))
      }
  }
}
