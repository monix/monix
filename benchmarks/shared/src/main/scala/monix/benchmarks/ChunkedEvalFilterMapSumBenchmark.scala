/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.eval.{Task => MonixTask}
import monix.reactive.{Observable => MonixObservable}
import monix.tail.Iterant
import org.openjdk.jmh.annotations._
import zio.stream.{Stream => ZStream}
import fs2.{Stream => FS2Stream}
import zio.{Chunk, UIO}

import scala.collection.immutable.IndexedSeq

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

  // All events that need to be streamed
  var allElements: IndexedSeq[Int] = _
  // For ensuring we get the result we expect
  var expectedSum: Long = _

  @Setup
  def setup(): Unit = {
    val chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    allElements = chunks.flatten
    expectedSum = allElements.sum
  }

  @Benchmark
  def fs2Stream = {
    val stream = FS2Stream
      // 1: iteration
      .apply(allElements:_*)
      // 2: collect buffers
      .chunkN(chunkSize)
      // 3: eval map
      .evalMap[MonixTask, Int](chunk => MonixTask(chunk.foldLeft(0)(_ + _)))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      .compile
      // 6: foldLeft
      .fold(0L)(_ + _)

    testResult(stream.runSyncUnsafe())
  }

  @Benchmark
  def monixIterant: Long = {
    val stream = Iterant[MonixTask]
      // 1: iteration
      .fromSeq(allElements)
      // 2: collect buffers
      .bufferTumbling(chunkSize)
      // 3: eval map
      .mapEval(seq => MonixTask(sumIntScala(seq)))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      // 6: foldLeft
      .foldLeftL(0L)(_ + _)

    testResult(stream.runSyncUnsafe())
  }

  @Benchmark
  def monixObservable(): Unit = {
    // N.B. chunks aren't needed for Monix's Observable ;-)
    val stream = MonixObservable
      // 1: iteration
      .fromIterable(allElements)
      // 2: collect buffers
      .bufferTumbling(chunkSize)
      // 3: eval map
      .mapEval[Int](seq => MonixTask(sumIntScala(seq)))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      // 6: foldLeft
      .foldLeftL(0L)(_ + _)

    testResult(stream.runSyncUnsafe())
  }

  @Benchmark
  def zioStream = {
    val stream = ZStream
      // 1: iteration
      .fromIterable(allElements)
      // 2: collect buffers
      .chunkN(chunkSize)
      // 3: eval map
      .mapChunksM(chunk => UIO(Chunk.single(chunk.foldLeft(0)(_ + _))))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      // 6: foldLeft
      .fold(0L)(_ + _)

    testResult(zioUntracedRuntime.unsafeRun(stream))
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
}
