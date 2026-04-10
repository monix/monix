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

import fs2.{ Stream => FS2Stream }
import monix.reactive.Observable
import org.openjdk.jmh.annotations._
import zio.stream.{ Stream => ZStream }

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark ObservableMapAccumulateBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ObservableMapAccumulateBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ObservableMapAccumulateBenchmark {
  @Param(Array("1000", "10000"))
  var n: Int = _

  @Benchmark
  def monixObservable() = {
    Observable
      .fromIterable(0 until n)
      .mapAccumulate(0) {
        case (acc, i) =>
          val added = acc + i
          (added, added)
      }
      .completedL
      .runSyncUnsafe()
  }

  @Benchmark
  def fs2Stream() = {
    FS2Stream
      .emits(0 until n)
      .mapAccumulate(0) {
        case (acc, i) =>
          val added = acc + i
          (added, added)
      }
      .compile
      .drain
  }

  @Benchmark
  def zioStream() = {
    val stream = ZStream
      .fromIterable(0 until n)
      .mapAccum(0) {
        case (acc, i) =>
          val added = acc + i
          (added, added)
      }
      .runDrain

    zioUntracedRuntime.unsafeRun(stream)
  }
}
