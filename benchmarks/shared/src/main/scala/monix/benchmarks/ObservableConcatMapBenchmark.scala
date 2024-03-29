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

import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.openjdk.jmh.annotations._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise }

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark ObservableConcatMapBenchmark
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
class ObservableConcatMapBenchmark {
  @Param(Array("100000"))
  var size: Int = _

  @Benchmark
  def onePerCycle(): Long = {
    val stream = Observable.range(0, size.toLong).flatMap { x =>
      Observable.now(x + 1)
    }
    sum(stream)
  }

  @Benchmark
  def morePerCycle(): Long = {
    val stream = Observable.range(0, size.toLong / 1000).flatMap { x =>
      Observable.range(x, x + 1000)
    }
    sum(stream)
  }

  def sum(stream: Observable[Long]): Long = {
    val p = Promise[Long]()
    stream.unsafeSubscribeFn(new Subscriber.Sync[Long] {
      val scheduler = global
      private[this] var sum: Long = 0

      def onError(ex: Throwable): Unit = {
        p.failure(ex)
        ()
      }

      def onComplete(): Unit = {
        p.success(sum)
        ()
      }

      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
    })
    Await.result(p.future, Duration.Inf)
  }
}
