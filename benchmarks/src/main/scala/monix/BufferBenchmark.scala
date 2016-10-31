/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/*
 * Sample run:
 *
 *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.BufferBenchmark"
 *
 * Which means "10 iterations" "5 warmup iterations" "1 fork" "1 thread".
 * Please note that benchmarks should be usually executed at least in
 * 10 iterations (as a rule of thumb), but more is better.
 */

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class BufferBenchmark {
  val parallelism = 4

  @Benchmark
  def monixUnbounded(): Long = {
    import monix.reactive.OverflowStrategy
    import monix.reactive.observers.Subscriber
    import monix.reactive.observers.BufferedSubscriber
    import monix.execution.Ack.Continue
    import monix.execution.Scheduler

    val promise = Promise[Long]()
    implicit val global: Scheduler = Scheduler.global

    val out: Subscriber[Long] = new Subscriber[Long] {
      private[this] var sum = 0L
      implicit val scheduler = global

      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit =
        promise.failure(ex)
      def onComplete(): Unit =
        promise.success(sum)
    }

    val buffer = BufferedSubscriber[Long](out, OverflowStrategy.Unbounded)

    val futures =
      for (i <- 0 until parallelism) yield Future {
        for (j <- 0 until 1000) buffer.onNext(j)
      }

    Future.sequence(futures).map(_ => buffer.onComplete())
    Await.result(promise.future, Duration.Inf)
  }

  @Benchmark
  def monixOverflowing(): Long = {
    import monix.reactive.OverflowStrategy
    import monix.reactive.observers.Subscriber
    import monix.reactive.observers.BufferedSubscriber
    import monix.execution.Ack.Continue
    import monix.execution.Scheduler

    val promise = Promise[Long]()
    implicit val global: Scheduler = Scheduler.global

    val out: Subscriber[Long] = new Subscriber[Long] {
      private[this] var sum = 0L
      implicit val scheduler = global

      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit =
        promise.failure(ex)
      def onComplete(): Unit =
        promise.success(sum)
    }

    val buffer = BufferedSubscriber[Long](out, OverflowStrategy.Fail(8000))

    val futures =
      for (i <- 0 until parallelism) yield Future {
        for (j <- 0 until 1000) buffer.onNext(j)
      }

    Future.sequence(futures).map(_ => buffer.onComplete())
    Await.result(promise.future, Duration.Inf)
  }

  @Benchmark
  def monifuUnbounded(): Long = {
    import monifu.reactive.OverflowStrategy
    import monifu.reactive.Subscriber
    import monifu.reactive.observers.BufferedSubscriber
    import monifu.reactive.Ack.Continue
    import monifu.concurrent.Scheduler

    val promise = Promise[Long]()
    implicit val global: Scheduler =
      monifu.concurrent.Implicits.globalScheduler

    val out: Subscriber[Long] = new Subscriber[Long] {
      private[this] var sum = 0L
      implicit val scheduler = global

      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit =
        promise.failure(ex)
      def onComplete(): Unit =
        promise.success(sum)
    }

    val buffer = BufferedSubscriber[Long](out, OverflowStrategy.Unbounded)
    val futures =
      for (i <- 0 until parallelism) yield Future {
        for (j <- 0 until 1000) buffer.onNext(j)
      }

    Future.sequence(futures).map(_ => buffer.onComplete())
    Await.result(promise.future, Duration.Inf)
  }

  @Benchmark
  def monifuOverflowing(): Long = {
    import monifu.reactive.OverflowStrategy
    import monifu.reactive.Subscriber
    import monifu.reactive.observers.BufferedSubscriber
    import monifu.reactive.Ack.Continue
    import monifu.concurrent.Scheduler

    val promise = Promise[Long]()
    implicit val global: Scheduler =
      monifu.concurrent.Implicits.globalScheduler

    val out: Subscriber[Long] = new Subscriber[Long] {
      private[this] var sum = 0L
      implicit val scheduler = global

      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit =
        promise.failure(ex)
      def onComplete(): Unit =
        promise.success(sum)
    }

    val buffer = BufferedSubscriber[Long](out, OverflowStrategy.Fail(8000))
    val futures =
      for (i <- 0 until parallelism) yield Future {
        for (j <- 0 until 1000) buffer.onNext(j)
      }

    Future.sequence(futures).map(_ => buffer.onComplete())
    Await.result(promise.future, Duration.Inf)
  }
}
