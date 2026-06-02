/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import monix.eval.{ Task => MonixTask }
import monix.reactive.{ Observable => MonixObservable }
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

/**
  * Benchmark designed to execute these operations:
  *
  *  1. iteration over an iterable
  *  2. sliding window of fixed size
  *  3. mapEval / flatMap
  *  4. filter
  *  5. map
  *  6. foldLeft
  *
  * If the benchmark does not execute these and exactly these operations,
  * then the measurement is not measuring the same thing across the board.
  *
  * To run the benchmarks and record results:
  *
  *     ./run-benchmark ChunkedEvalFilterMapSumBenchmark
  *
  * This will generate results in `./results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ChunkedEvalFilterMapSumBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkedEvalFilterMapSumBenchmark {
  @Param(Array("1000"))
  var chunkCount: Int = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  // For ensuring we get the result we expect
  var expectedSum: Long = _

  var allElements: IndexedSeq[Int] = _
  var chunks: IndexedSeq[Array[Int]] = _

  @Setup
  def setup(): Unit = {
    chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    allElements = chunks.flatten
    expectedSum = allElements.map(_.toLong).sum
  }

  @Benchmark
  def monixObservable() = {
    val stream = MonixObservable
      .fromIterable(allElements)
      .bufferTumbling(chunkSize)
      .mapEval[Int](seq => MonixTask(sumIntScala(seq)))
      .filter(_ > 0)
      .map(_.toLong)
      .foldLeftL(0L)(_ + _)

    testResult(stream.runSyncUnsafe())
  }

  @Benchmark
  def monixObservablePreChunked() = {
    val stream = MonixObservable
      .fromIterable(chunks)
      .mapEval[Int](seq => MonixTask(sumIntScala(seq)))
      .filter(_ > 0)
      .map(_.toLong)
      .foldLeftL(0L)(_ + _)

    testResult(stream.runSyncUnsafe())
  }

  def testResult(r: Long): Long = {
    if (r == 0 || r != expectedSum) {
      throw new RuntimeException(s"received: $r != expected: $expectedSum")
    }
    r
  }

  def sumIntScala(seq: Iterable[Int]): Int = {
    val cursor = seq.iterator
    var sum = 0
    while (cursor.hasNext) {
      sum += cursor.next()
    }
    sum
  }

  def sumIntScala(cursor: Iterator[Int]): Int = {
    var sum = 0
    while (cursor.hasNext) {
      sum += cursor.next()
    }
    sum
  }
}
