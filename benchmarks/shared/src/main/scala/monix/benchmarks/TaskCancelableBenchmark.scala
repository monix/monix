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

package monix.benchmarks

import java.util.concurrent.TimeUnit
import monix.eval.Task
import org.openjdk.jmh.annotations._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** To do comparative benchmarks between versions:
 *
 *     benchmarks/run-benchmark TaskCancelableBenchmark
 *
 * This will generate results in `benchmarks/results`.
 *
 * Or to run the benchmark from within SBT:
 *
 *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.TaskCancelableBenchmark
 *
 * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
 * Please note that benchmarks should be usually executed at least in
 * 10 iterations (as a rule of thumb), but more is better.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskCancelableBenchmark {
  @Param(Array("3000"))
  var size: Int = _

  @Benchmark
  def uncancelable(): Int = {
    def loop(i: Int): Task[Int] =
      Task.eval(i).uncancelable.flatMap { i =>
        if (i > 0) loop(i - 1)
        else Task.now(0)
      }

    Await.result(loop(size).runAsync, Duration.Inf)
  }

  @Benchmark
  def onCancelRaiseError(): Int = {
    val e = new RuntimeException("dummy")
    def loop(i: Int): Task[Int] =
      Task.eval(i).onCancelRaiseError(e).flatMap { i =>
        if (i > 0) loop(i - 1)
        else Task.now(0)
      }

    Await.result(loop(size).runAsync, Duration.Inf)
  }

  @Benchmark
  def bracket(): Int = {
    var effect = 0
    def loop(i: Int): Task[Int] =
      Task.eval(i).bracket(Task.eval(_))(_ => Task.eval { effect += 1 })
        .flatMap { i =>
          if (i > 0) loop(i - 1)
          else Task.now(effect)
        }

    Await.result(loop(size).runAsync, Duration.Inf)
  }
}
