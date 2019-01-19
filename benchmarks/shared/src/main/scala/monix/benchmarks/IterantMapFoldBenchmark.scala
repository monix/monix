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
import monix.eval.Coeval
import monix.tail.Iterant
import org.openjdk.jmh.annotations._

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark IterantMapFoldBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.IterantMapFoldBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IterantMapFoldBenchmark {
  import IterantMapFoldBenchmark._

  @Benchmark
  def granular(): Long =
    granularRef.map(_ + 1).foldLeftL(0L)(_ + _).value

  @Benchmark
  def batched(): Long =
    batchedRef.map(_ + 1).foldLeftL(0L)(_ + _).value
}

object IterantMapFoldBenchmark {
  val size = 1000

  val granularRef: Iterant[Coeval, Int] = {
    var stream = Iterant[Coeval].empty[Int]
    var idx = 0
    while (idx < size) {
      stream = idx +: stream
      idx += 1
    }
    stream
  }

  val batchedRef: Iterant[Coeval, Int] = {
    var stream = Iterant[Coeval].empty[Int]
    var idx = 0
    while (idx < size) {
      val rest = stream
      stream = rest ++ Iterant[Coeval].range(0, 10)
      idx += 1
    }
    stream
  }
}
