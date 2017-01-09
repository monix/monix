/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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
import monix.iterant.Cursor
import org.openjdk.jmh.annotations._

/**
  * Sample run:
  *
  *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.benchmarks.CursorBenchmark"
  *
  * Which means "20 iterations" of "2 seconds" each, "20 warm-up
  * iterations" of "2 seconds" each, "1 fork", "1 thread".  Please note
  * that benchmarks should be usually executed at least in 10
  * iterations (as a rule of thumb), but the more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CursorBenchmark {
  // Number of threads that push messages
  @Param(Array("128", "1024", "4096"))
  var size = 0

  @Benchmark
  def fromIterator: Long = {
    val iterator = (0 until size).toArray.iterator

    Cursor.fromIterator(iterator)
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .collect { case x => x + 1 }
      .map(_ + 2)
      .foldLeft(0L)(_+_)
  }

  @Benchmark
  def fromArray: Long = {
    val iterator = (0 until size).toArray

    Cursor.fromArray(iterator)
      .map(_ + 1)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .collect { case x => x + 1 }
      .map(_ + 2)
      .foldLeft(0L)(_+_)
  }
}
