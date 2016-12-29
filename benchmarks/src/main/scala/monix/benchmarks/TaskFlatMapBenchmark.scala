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

package monix.benchmarks

import java.util.concurrent.TimeUnit
import monix.eval.Callback
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Sample run:
  *
  *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.benchmarks.TaskFlatMapBenchmark"
  *
  * Which means "10 iterations" "10 warmup iterations" "1 fork" "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskFlatMapBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def monixApply(): Int = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.apply(i + 1).flatMap(loop)
      else Task.apply(i)

    val task = Task.apply(0).flatMap(loop)
    Await.result(task.runAsync, Duration.Inf)
  }

  @Benchmark
  def monixEval(): Int = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.eval(i + 1).flatMap(loop)
      else Task.eval(i)

    val task = Task.eval(0).flatMap(loop)
    var result: Int = 0
    task.runAsync(new Callback[Int] {
      def onSuccess(value: Int): Unit =
        result = value
      def onError(ex: Throwable): Unit =
        throw ex
    })
    result
  }

  @Benchmark
  def monixNow(): Int = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.now(i + 1).flatMap(loop)
      else Task.now(i)

    val task = Task.now(0).flatMap(loop)
    var result: Int = 0
    task.runAsync(new Callback[Int] {
      def onSuccess(value: Int): Unit =
        result = value
      def onError(ex: Throwable): Unit =
        throw ex
    })
    result
  }
}

object TaskFlatMapBenchmark {
  import monix.execution.Scheduler

  implicit val monixScheduler: Scheduler = {
    import monix.execution.schedulers.ExecutionModel.SynchronousExecution
    Scheduler.computation().withExecutionModel(SynchronousExecution)
  }
}