/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.effect.IO
import cats.implicits._
import monix.eval.Task
import org.openjdk.jmh.annotations._
import cats.effect.implicits._
import zio.ZIO

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark TaskSequenceBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run monix.benchmarks.TaskSequenceBenchmark
  *     The above test will take default values as "10 iterations", "10 warm-up iterations",
  *     "2 forks", "1 thread".
  *
  *     Or to specify custom values use below format:
  *
  *     jmh:run -i 20 -wi 20 -f 4 -t 2 monix.benchmarks.TaskGatherBenchmark
  *
  * Which means "20 iterations", "20 warm-up iterations", "4 forks", "2 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskSequenceBenchmark {
  @Param(Array("100", "1000"))
  var count: Int = _

  @Param(Array("10"))
  var parallelism: Int = _

  @Benchmark
  def catsSequence(): Long = {
    val tasks = (0 until count).map(_ => IO(1)).toList
    val result = tasks.sequence.map(_.sum.toLong)
    result.unsafeRunSync()
  }

  @Benchmark
  def catsParSequence(): Long = {
    val tasks = (0 until count).map(_ => IO(1)).toList
    val result = tasks.parSequence.map(_.sum.toLong)
    result.unsafeRunSync()
  }

  @Benchmark
  def catsParSequenceN(): Long = {
    val tasks = (0 until count).map(_ => IO(1)).toList
    val result = tasks.parSequenceN(parallelism).map(_.sum.toLong)
    result.unsafeRunSync()
  }

  @Benchmark
  def monixSequence(): Long = {
    val tasks = (0 until count).map(_ => Task.eval(1)).toList
    val result = Task.sequence(tasks).map(_.sum.toLong)
    result.runSyncUnsafe()
  }

  @Benchmark
  def monixGather(): Long = {
    val tasks = (0 until count).map(_ => Task.eval(1)).toList
    val result = Task.gather(tasks).map(_.sum.toLong)
    result.runSyncUnsafe()
  }

  @Benchmark
  def monixGatherUnordered(): Long = {
    val tasks = (0 until count).map(_ => Task.eval(1)).toList
    val result = Task.gatherUnordered(tasks).map(_.sum.toLong)
    result.runSyncUnsafe()
  }

  @Benchmark
  def monixGatherN(): Long = {
    val tasks = (0 until count).map(_ => Task.eval(1)).toList
    val result = Task.gatherN(parallelism)(tasks).map(_.sum.toLong)
    result.runSyncUnsafe()
  }

  @Benchmark
  def zioSequence(): Long = {
    val tasks = (0 until count).map(_ => ZIO.effectTotal(1)).toList
    val result = ZIO.sequence(tasks).map(_.sum.toLong)
    zioUntracedRuntime.unsafeRun(result)
  }

  @Benchmark
  def zioParSequence(): Long = {
    val tasks = (0 until count).map(_ => ZIO.effectTotal(1)).toList
    val result = ZIO.collectAllPar(tasks).map(_.sum.toLong)
    zioUntracedRuntime.unsafeRun(result)
  }

  @Benchmark
  def zioParSequenceN(): Long = {
    val tasks = (0 until count).map(_ => ZIO.effectTotal(1)).toList
    val result = ZIO.collectAllParN(parallelism)(tasks).map(_.sum.toLong)
    zioUntracedRuntime.unsafeRun(result)
  }

}