/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.openjdk.jmh.annotations._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark ObservableMapTaskBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ObservableMapTaskBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ObservableMapTaskBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def run(): Long = {
    val stream = Observable.range(0, size).mapTask { x =>
      Task.now(x + 1)
    }
    sum(stream)
  }

  def sum(stream: Observable[Long]): Long = {
    val p = Promise[Long]()
    stream.unsafeSubscribeFn(new Subscriber.Sync[Long] {
      val scheduler = global
      private[this] var sum: Long = 0

      def onError(ex: Throwable): Unit =
        p.failure(ex)
      def onComplete(): Unit =
        p.success(sum)
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
    })
    Await.result(p.future, Duration.Inf)
  }
}
