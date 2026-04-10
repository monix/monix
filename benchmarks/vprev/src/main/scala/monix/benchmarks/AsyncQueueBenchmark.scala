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

package monix.benchmarks

import java.util.concurrent.TimeUnit
import monix.execution.CancelableFuture
import monix.execution.AsyncQueue
import org.openjdk.jmh.annotations._
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark AsyncQueueBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run monix.benchmarks.TaskShiftBenchmark
  *     The above test will take default values as "10 iterations", "10 warm-up iterations",
  *     "2 forks", "1 thread".
  *
  *     Or to specify custom values use below format:
  *
  *     jmh:run -i 20 -wi 20 -f 4 -t 2 monix.benchmarks.TaskShiftBenchmark
  *
  * Which means "20 iterations", "20 warm-up iterations", "4 forks", "2 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10)
@Warmup(iterations = 10)
@Fork(2)
@Threads(1)
class AsyncQueueBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def spsc(): Long = {
    test(producers = 1, workers = 1)
  }

  @Benchmark
  def spmc(): Long = {
    test(producers = 1, workers = 4)
  }

  @Benchmark
  def mpmc(): Long = {
    test(producers = 4, workers = 4)
  }

  def test(producers: Int, workers: Int): Long = {
    val queue = AsyncQueue.unbounded[Int]()
    val workers = 1

    def producer(n: Int): Future[Long] =
      if (n > 0)
        CancelableFuture.successful(queue.offer(1)).flatMap(_ => producer(n - 1))
      else
        CancelableFuture.successful(0L)

    def consumer(n: Int, acc: Long): Future[Long] =
      if (n > 0) queue.poll().flatMap(i => consumer(n - 1, acc + i))
      else CancelableFuture.successful(acc)

    val producerTasks = (0 until producers).map(_ => producer(size / producers))
    val workerTasks = (0 until workers).map(_ => consumer(size / workers, 0))

    val futures = producerTasks ++ workerTasks
    val r = for (l <- Future.sequence(futures)) yield l.sum
    Await.result(r, Duration.Inf)
  }
}
