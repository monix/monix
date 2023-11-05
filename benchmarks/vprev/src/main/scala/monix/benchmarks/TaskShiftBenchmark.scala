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

/*
package monix.benchmarks

import java.util.concurrent.TimeUnit
import monix.eval.Task
import monix.execution.cancelables.Cancelable
import scala.util.control.NonFatal
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** To do comparative benchmarks between versions:
 *
 *     benchmarks/run-benchmark TaskShiftBenchmark
 *
 * This will generate results in `benchmarks/results`.
 *
 * Or to run the benchmark from within SBT:
 *
 *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.TaskShiftBenchmark
 *
 * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
 * Please note that benchmarks should be usually executed at least in
 * 10 iterations (as a rule of thumb), but more is better.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskShiftBenchmark {
  @Param(Array("3000"))
  var size: Int = _

  @Benchmark
  def trampolinedShift1(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        TaskShiftBenchmark.trampolinedShift1.map(_ => i + 1).flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def trampolinedShift2(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        TaskShiftBenchmark.trampolinedShift2.map(_ => i + 1).flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def forkedShift(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        Task.shift.map(_ => i + 1).flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def lightAsync(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        TaskShiftBenchmark.async[Int](_.onSuccess(i + 1)).flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def executeWithOptions(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        Task.now(i + 1).executeWithOptions(x => x).flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }


  @Benchmark
  def createNonCancelable(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        Task.create[Int] { (_, cb) => cb.onSuccess(i + 1); Cancelable.empty }.flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def createCancelable(): Int = {
    def loop(i: Int): Task[Int] =
      if (i < size)
        Task.create[Int] { (_, cb) => cb.onSuccess(i + 1); Cancelable(() => {}) }.flatMap(loop)
      else
        Task.pure(i)

    val task = Task.pure(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }
}

object TaskShiftBenchmark {
  import monix.execution.Callback

  def async[A](k: Callback[Throwable, A] => Unit): Task[A] =
    Task.unsafeCreate { (ctx, cb) =>
      try k(Callback.async(cb)(ctx.scheduler)) catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          ctx.scheduler.reportFailure(ex)
      }
    }

  val trampolinedShift1: Task[Unit] =
    async(_.onSuccess(()))

  val trampolinedShift2: Task[Unit] =
    Task.unsafeCreate { (ctx, cb) =>
      ctx.scheduler.executeTrampolined(() => cb.onSuccess(()))
    }
}
 */
