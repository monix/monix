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

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.openjdk.jmh.annotations._
import scalaz.Nondeterminism
import scalaz.concurrent.{Task => ScalazTask}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/*
 * Sample run:
 *
 *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.TaskGatherBenchmark"
 *
 * Which means "10 iterations" "5 warmup iterations" "1 fork" "1 thread".
 * Please note that benchmarks should be usually executed at least in
 * 10 iterations (as a rule of thumb), but more is better.
 */

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskGatherBenchmark {
  @Benchmark
  def sequenceFutureA(): Long = {
    val futures = (0 until 1000).map(_ => Future(1)).toList
    val f: Future[Long] = Future.sequence(futures).map(_.sum.toLong)
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def sequenceMonixA(): Long = {
    val tasks = (0 until 1000).map(_ => Task(1)).toList
    val f = Task.sequence(tasks).map(_.sum.toLong).runAsync
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def sequenceMonixS(): Long = {
    val tasks = (0 until 1000).map(_ => Task.eval(1)).toList
    val f = Task.sequence(tasks).map(_.sum.toLong).runAsync
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def sequenceScalazA(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask(1)).toList
    val init = ScalazTask.delay(ListBuffer.empty[Int])
    tasks.foldLeft(init)((acc,elem) => acc.flatMap(lb => elem.map(e => lb += e)))
      .map(_.toList.sum.toLong).unsafePerformSync
  }

  @Benchmark
  def sequenceScalazS(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask.delay(1)).toList
    val init = ScalazTask.delay(ListBuffer.empty[Int])
    tasks.foldLeft(init)((acc,elem) => acc.flatMap(lb => elem.map(e => lb += e)))
      .map(_.toList.sum.toLong).unsafePerformSync
  }

  @Benchmark
   def gatherMonixA(): Long = {
     val tasks = (0 until 1000).map(_ => Task(1)).toList
     val f = Task.gather(tasks).map(_.sum.toLong).runAsync
     Await.result(f, Duration.Inf)
   }

   @Benchmark
   def gatherMonixS(): Long = {
     val tasks = (0 until 1000).map(_ => Task.eval(1)).toList
     val f = Task.gather(tasks).map(_.sum.toLong).runAsync
     Await.result(f, Duration.Inf)
   }

  @Benchmark
  def gatherScalazA(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask(1)).toList
    Nondeterminism[ScalazTask].gather(tasks).map(_.sum.toLong).unsafePerformSync
  }

  @Benchmark
  def gatherScalazS(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask.delay(1)).toList
    Nondeterminism[ScalazTask].gather(tasks).map(_.sum.toLong).unsafePerformSync
  }

   @Benchmark
   def unorderedMonixA(): Long = {
     val tasks = (0 until 1000).map(_ => Task(1)).toList
     val f = Task.gatherUnordered(tasks).map(_.sum.toLong).runAsync
     Await.result(f, Duration.Inf)
   }

   @Benchmark
   def unorderedMonixS(): Long = {
     val tasks = (0 until 1000).map(_ => Task.eval(1)).toList
     val f = Task.gatherUnordered(tasks).map(_.sum.toLong).runAsync
     Await.result(f, Duration.Inf)
   }

  @Benchmark
  def unorderedScalazA(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask(1)).toList
    Nondeterminism[ScalazTask].gatherUnordered(tasks).map(_.sum.toLong).unsafePerformSync
  }

  @Benchmark
  def unorderedScalazS(): Long = {
    val tasks = (0 until 1000).map(_ => ScalazTask.delay(1)).toList
    Nondeterminism[ScalazTask].gatherUnordered(tasks).map(_.sum.toLong).unsafePerformSync
  }
}
