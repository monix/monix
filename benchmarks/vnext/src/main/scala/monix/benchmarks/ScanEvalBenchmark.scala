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
import monix.eval.Task
import monix.execution.ExecutionModel.SynchronousExecution
import monix.reactive.Observable
import monix.tail.Iterant
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** To run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ScanEvalBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 fork", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ScanEvalBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def obsScanEvalPure() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .scanTask(Task.now(0L)) { (s, a) => Task.now(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def obsScanEvalDelay() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .scanTask(Task.eval(0L)) { (s, a) => Task.eval(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def obsScanEvalFork() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .scanTask(Task(0L)) { (s, a) => Task(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def obsFlatScanPure() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .flatScan(0L) { (s, a) => Observable.now(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def obsFlatScanDelay() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .flatScan(0L) { (s, a) => Observable.eval(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def obsFlatScanFork() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Observable.range(0, size)
      .flatScan(0L) { (s, a) => Observable.eval(s + a).executeWithFork }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }


  @Benchmark
  def iterScanEvalPure() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Iterant[Task].range(0, size)
      .scanEval(Task.now(0L)) { (s, a) => Task.now(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def iterScanEvalDelay() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Iterant[Task].range(0, size)
      .scanEval(Task.eval(0L)) { (s, a) => Task.eval(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def iterScanEvalFork() = {
    import ScanEvalBenchmark.monixScheduler

    val f = Iterant[Task].range(0, size)
      .scanEval(Task(0L)) { (s, a) => Task(s + a) }
      .foldLeftL(0L)(_ + _)
      .runAsync

    Await.result(f, Duration.Inf)
  }
}

object ScanEvalBenchmark {
  import monix.execution.Scheduler

  implicit val monixScheduler: Scheduler = {
    import monix.execution.Scheduler.global
    global.withExecutionModel(SynchronousExecution)
  }
}
