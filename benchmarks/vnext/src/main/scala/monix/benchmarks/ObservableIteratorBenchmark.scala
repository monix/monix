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

import monix.benchmarks
import monix.execution.Ack.Continue
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.openjdk.jmh.annotations._

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
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ObservableIteratorBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ObservableIteratorBenchmark {
  @Param(Array("1000"))
  var chunkCount: Int = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  // All events that need to be streamed
  var allElements: IndexedSeq[Int] = _
  var chunks: IndexedSeq[Array[Int]] = _

  @Setup
  def setup(): Unit = {
    chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    allElements = chunks.flatten
  }

  @Benchmark
  def bufferTumbling(): Int = {
    val stream = Observable
      .fromIteratorUnsafe(allElements.iterator)
      .bufferTumbling(chunkSize)
      .map(sumIntScala)

    sum(stream)
  }

  @Benchmark
  def fromIteratorBufferedUnsafe(): Int = {
    val stream = Observable
      .fromIteratorBufferedUnsafe(allElements.iterator, chunkSize)
      .map(sumIntScala)

    sum(stream)
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

  def sumIntScala(seq: Iterable[Int]): Int = {
    val cursor = seq.iterator
    var sum = 0
    while (cursor.hasNext) {
      sum += cursor.next()
    }
    sum
  }
}
