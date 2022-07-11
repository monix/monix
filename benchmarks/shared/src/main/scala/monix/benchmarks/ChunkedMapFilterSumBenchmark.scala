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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Keep, Sink => AkkaSink, Source => AkkaSource }
import fs2.{ Stream => FS2Stream }
import monix.benchmarks
import monix.execution.Ack.Continue
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.openjdk.jmh.annotations._
import zio.stream.{ Stream => ZStream }

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark ChunkedMapFilterSumBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ChunkedMapFilterSumBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkedMapFilterSumBenchmark {
  @Param(Array("1000"))
  var chunkCount: Int = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  // All events that need to be streamed
  var allElements: IndexedSeq[Int] = _
  var allElementsVector: Vector[Int] = _

  var chunks: IndexedSeq[Array[Int]] = _
  var fs2Chunks: IndexedSeq[fs2.Chunk[Int]] = _
  var zioChunks: IndexedSeq[zio.Chunk[Int]] = _

  @Setup
  def setup(): Unit = {
    chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    fs2Chunks = chunks.map(fs2.Chunk.array)
    zioChunks = chunks.map(zio.Chunk.fromArray)
    allElements = chunks.flatten
    allElementsVector = allElements.toVector
  }

  implicit val system = ActorSystem("benchmarks", defaultExecutionContext = Some(scheduler))

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    ()
  }

  @Benchmark
  def monixObservable(): Int = {
    val stream = Observable
      .fromIterable(allElements)
      .map(_ + 1)
      .filter(_ % 2 == 0)

    sum(stream)
  }

  @Benchmark
  def vector(): Int = {
    allElementsVector
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .sum
  }

  @Benchmark
  def iterator(): Int = {
    allElements.iterator
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .sum
  }

  @Benchmark
  def whileLoop(): Int = {
    val cursor = allElements.iterator
    var sum = 0
    while (cursor.hasNext) {
      val next = cursor.next() + 1
      if (next % 2 == 0) sum += next
    }
    sum
  }

  @Benchmark
  def fs2Stream(): Int = {
    val stream = FS2Stream(fs2Chunks: _*)
      .flatMap(FS2Stream.chunk)
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .compile
      .fold(0)(_ + _)

    stream
  }

  @Benchmark
  def zioStream(): Int = {
    val stream = ZStream
      .fromChunks(zioChunks: _*)
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .runSum

    zioUntracedRuntime.unsafeRun(stream)
  }

  @Benchmark
  def akkaStream(): Long = {
    val stream = AkkaSource(allElements)
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .toMat(AkkaSink.fold(0L)(_ + _))(Keep.right)

    Await.result(stream.run(), Duration.Inf)
  }

  def sum(stream: Observable[Int]): Int = {
    val p = Promise[Int]()
    stream.unsafeSubscribeFn(new Subscriber.Sync[Int] {
      val scheduler = benchmarks.scheduler
      private[this] var sum: Int = 0

      def onError(ex: Throwable): Unit =
        p.failure(ex)
      def onComplete(): Unit =
        p.success(sum)
      def onNext(elem: Int) = {
        sum += elem
        Continue
      }
    })
    Await.result(p.future, Duration.Inf)
  }
}
