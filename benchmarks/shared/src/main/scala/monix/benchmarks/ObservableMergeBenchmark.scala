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
import monix.eval.{ Task => MonixTask }
import monix.reactive.Observable
import org.openjdk.jmh.annotations._
import zio.ZIO
import zio.stream.{ Stream => ZStream }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.util.Try

/** To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark ObservableMergeBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within SBT:
  *
  *     jmh:run -i 10 -wi 10 -f 2 -t 1 monix.benchmarks.ObservableMergeBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ObservableMergeBenchmark {
  @Param(Array("100", "1000"))
  var streams: Int = _

  implicit val system = ActorSystem("benchmarks", defaultExecutionContext = Some(scheduler))

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    ()
  }

  @Benchmark
  def monixObservable() = {
    Observable
      .fromIterable(0 until streams)
      .mergeMap(i => Observable.fromTask(MonixTask.eval(i)))
      .completedL
      .runSyncUnsafe()
  }

  @Benchmark
  def fs2Stream() = {
    FS2Stream
      .emits(0 until streams)
      .map(i => FS2Stream.eval(MonixTask.eval(i)))
      .covary[monix.eval.Task]
      .parJoinUnbounded
      .compile
      .drain
      .runSyncUnsafe()
  }

  @Benchmark
  def zioStream() = {
    val stream = ZStream
      .fromIterable(0 until streams)
      .flatMapPar(Int.MaxValue)(i => ZStream.fromEffect(ZIO.apply(i)))
      .runDrain

    zioUntracedRuntime.unsafeRun(stream)
  }

  @Benchmark
  def akkaStream(): Long = {
    val stream = AkkaSource(0 until streams)
      .flatMapMerge(Int.MaxValue, i => AkkaSource.lazyFuture(() => Future.fromTry(Try(i))))
      .toMat(AkkaSink.fold(0L)(_ + _))(Keep.right)

    Await.result(stream.run(), Duration.Inf)
  }
}
