/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix

import cats.effect.{ ContextShift, IO }
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import zio.BootstrapRuntime
import zio.internal.{ Platform, Tracing }

import scala.concurrent.ExecutionContext

package object benchmarks {
  /** Scheduler used for all testing, avoiding forced async boundaries. */
  implicit val scheduler: Scheduler =
    global.withExecutionModel(SynchronousExecution)

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  val zioUntracedRuntime = new BootstrapRuntime {
    override val platform = Platform.benchmark.withTracing(Tracing.disabled)
  }
}
