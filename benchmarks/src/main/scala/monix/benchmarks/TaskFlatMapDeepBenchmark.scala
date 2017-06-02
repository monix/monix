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
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Sample run:
  *
  *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.benchmarks.TaskFlatMapDeepBenchmark"
  *
  * Which means "10 iterations" "10 warmup iterations" "1 fork" "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskFlatMapDeepBenchmark {
  @Param(Array("15"))
  var depth: Int = _

  @Benchmark
  def monixApply(): BigInt = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def fib(n: Int): Task[BigInt] =
      if (n <= 1) Task(n) else
        fib(n-1).flatMap { a =>
          fib(n-2).flatMap(b => Task(a + b))
        }

    Await.result(fib(depth).runAsync, Duration.Inf)
  }

  @Benchmark
  def monixEval(): BigInt = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def fib(n: Int): Task[BigInt] =
      if (n <= 1) Task.eval(n) else
        fib(n-1).flatMap { a =>
          fib(n-2).flatMap(b => Task.eval(a + b))
        }

    fib(depth).runSyncMaybe.right.get
  }

  @Benchmark
  def monixNow(): BigInt = {
    import TaskFlatMapBenchmark.monixScheduler
    import monix.eval.Task

    def fib(n: Int): Task[BigInt] =
      if (n <= 1) Task.now(n) else
        fib(n-1).flatMap { a =>
          fib(n-2).flatMap(b => Task.now(a + b))
        }

    fib(depth).runSyncMaybe.right.get
  }

  // @Benchmark
  // def scalazApply(): BigInt = {
  //   import scalaz.concurrent.Task

  //   def fib(n: Int): Task[BigInt] =
  //     if (n <= 1) Task(n) else
  //       fib(n-1).flatMap { a =>
  //         fib(n-2).flatMap(b => Task(a + b))
  //       }

  //   fib(depth).unsafePerformSync
  // }

  // @Benchmark
  // def scalazEval(): BigInt = {
  //   import scalaz.concurrent.Task

  //   def fib(n: Int): Task[BigInt] =
  //     if (n <= 1) Task.delay(n) else
  //       fib(n-1).flatMap { a =>
  //         fib(n-2).flatMap(b => Task.delay(a + b))
  //       }

  //   fib(depth).unsafePerformSync
  // }

  // @Benchmark
  // def scalazNow(): BigInt = {
  //   import scalaz.concurrent.Task

  //   def fib(n: Int): Task[BigInt] =
  //     if (n <= 1) Task.now(n) else
  //       fib(n-1).flatMap { a =>
  //         fib(n-2).flatMap(b => Task.now(a + b))
  //       }

  //   fib(depth).unsafePerformSync
  // }
}

object TaskFlatMapDeepBenchmark {
  import monix.execution.Scheduler

  implicit val monixScheduler: Scheduler = {
    import monix.execution.ExecutionModel.SynchronousExecution
    Scheduler.computation().withExecutionModel(SynchronousExecution)
  }
}