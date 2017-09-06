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
import monix.reactive.Observable
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** To do comparative benchmarks between Monix versions:
  *
  *     benchmarks/run-benchmark ObservableSuspendBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ObservableSuspendBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 fork", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ObservableSuspendBenchmark {
  @Param(Array("100"))
  var size: Int = _

  @Benchmark
  def simpleSuspend(): Option[Int] = {
    import ObservableSuspendBenchmark.monixScheduler
    val f = Observable.suspend(Observable.eval(10)).runAsyncGetLast
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def suspendLoop(): Option[Long] = {
    import ObservableSuspendBenchmark.monixScheduler

    def loop(i: Int, n: Long): Observable[Long] =
      Observable.suspend {
        if (i > 0) loop(i - 1, n + i)
        else Observable.now(n)
      }

    Await.result(loop(size, 0).runAsyncGetLast, Duration.Inf)
  }

  @Benchmark
  def concatLoop(): Option[Int] = {
    import ObservableSuspendBenchmark.monixScheduler

    def loop(i: Int): Observable[Int] =
      Observable.now(i) ++ Observable.suspend {
        if (i > 0) loop(i - 1)
        else Observable.now(0)
      }

    Await.result(loop(size).sumF.runAsyncGetLast, Duration.Inf)
  }
}


object ObservableSuspendBenchmark {
  import monix.execution.Scheduler

  implicit val monixScheduler: Scheduler = {
    import monix.execution.Scheduler.global
    global.withExecutionModel(SynchronousExecution)
  }
}
