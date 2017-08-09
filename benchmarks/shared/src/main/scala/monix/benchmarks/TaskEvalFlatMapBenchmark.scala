/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.benchmarks

import java.util.concurrent.TimeUnit

import monix.execution.ExecutionModel.SynchronousExecution
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** To do comparative benchmarks between Monix versions:
  *
  *     benchmarks/run-benchmark TaskFlatMapLongLoopBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.TaskFlatMapLongLoopBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 fork", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskFlatMapLongLoopBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def now(): Int = {
    import TaskEvalFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.now(i + 1).flatMap(loop)
      else Task.now(i)

    Task.now(0).flatMap(loop)
      .runSyncMaybe.right.get
  }

  @Benchmark
  def eval(): Int = {
    import TaskEvalFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.eval(i + 1).flatMap(loop)
      else Task.eval(i)

    Task.eval(0).flatMap(loop)
      .runSyncMaybe.right.get
  }

  @Benchmark
  def async(): Int = {
    import TaskEvalFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task(i + 1).flatMap(loop)
      else Task(i)

    Await.result(Task(0).flatMap(loop).runAsync, Duration.Inf)
  }
}

object TaskEvalFlatMapBenchmark {
  import monix.execution.Scheduler

  implicit val monixScheduler: Scheduler = {
    import monix.execution.Scheduler.global
    global.withExecutionModel(SynchronousExecution)
  }
}