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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink => AkkaSink, Source => AkkaSource}
import fs2.{Stream => FS2Stream}
import monix.eval.{Task => MonixTask}
import monix.reactive.{Observable => MonixObservable}
import org.openjdk.jmh.annotations._
import zio.stream.{Stream => ZStream}
import zio.{Chunk, UIO}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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
  implicit val system = ActorSystem("benchmarks", defaultExecutionContext = Some(scheduler))

  @Param(Array("1000"))
  var chunkCount: Int = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  // All events that need to be streamed
  var allElements: IndexedSeq[Int] = _
  // For ensuring we get the result we expect
  var expectedSum: Long = _

  var chunks: IndexedSeq[Array[Int]] = _
  var fs2Chunks: IndexedSeq[fs2.Chunk[Int]] = _
  var zioChunks: IndexedSeq[zio.Chunk[Int]] = _

  @Setup
  def setup(): Unit = {
    chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    fs2Chunks = chunks.map(fs2.Chunk.array)
    zioChunks = chunks.map(zio.Chunk.fromArray)
    allElements = chunks.flatten
    expectedSum = allElements.sum.toLong
  }

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    ()
  }

  @Benchmark
  def fs2Stream = {
    val stream = FS2Stream
      // 1: iteration
      .apply(allElements: _*)
      // 2: collect buffers
      .chunkN(chunkSize)
      // 3: eval map
      .evalMap[MonixTask, Int](chunk => MonixTask(sumIntScala(chunk.iterator)))
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
  def fs2StreamPreChunked = {
    val stream = FS2Stream(fs2Chunks: _*)
      .evalMap[MonixTask, Int](chunk => MonixTask(sumIntScala(chunk.iterator)))
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
  def monixObservable() = {
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
  def monixObservablePreChunked() = {
    // N.B. chunks aren't needed for Monix's Observable ;-)
    val stream = MonixObservable
      // 1: iteration
      .fromIterable(chunks)
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
      .mapChunksM(chunk => UIO(Chunk.single(sumIntScala(chunk))))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      // 6: foldLeft
      .fold(0L)(_ + _)

    testResult(zioUntracedRuntime.unsafeRun(stream))
  }

  @Benchmark
  def zioStreamPreChunked = {
    val stream = ZStream
      // 1: iteration
      .fromChunks(zioChunks: _*)
      // 3: eval map
      .mapChunksM(chunk => UIO(Chunk.single(sumIntScala(chunk))))
      // 4: filter
      .filter(_ > 0)
      // 5: map
      .map(_.toLong)
      // 6: foldLeft
      .fold(0L)(_ + _)

    testResult(zioUntracedRuntime.unsafeRun(stream))
  }

  @Benchmark
  def akkaStreams = {
    // N.B. Akka Streams don't do chunks
    val stream: RunnableGraph[Future[Long]] = AkkaSource
      .fromIterator(() => allElements.iterator)
      .sliding(chunkSize, chunkSize)
      .mapAsync(1)(seq => Future(sumIntScala(seq))(monix.execution.schedulers.TrampolineExecutionContext.immediate))
      .filter(_ > 0)
      .map(_.toLong)
      .toMat(AkkaSink.fold(0L)(_ + _))(Keep.right)

    testResult(Await.result(stream.run(), Duration.Inf))
  }

  @Benchmark
  def akkaStreamsPreChunked = {
    // N.B. Akka Streams don't do chunks
    val stream: RunnableGraph[Future[Long]] = AkkaSource
      .fromIterator(() => chunks.iterator)
      .mapAsync(1)(seq => Future(sumIntScala(seq))(monix.execution.schedulers.TrampolineExecutionContext.immediate))
      .filter(_ > 0)
      .map(_.toLong)
      .toMat(AkkaSink.fold(0L)(_ + _))(Keep.right)

    testResult(Await.result(stream.run(), Duration.Inf))

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
