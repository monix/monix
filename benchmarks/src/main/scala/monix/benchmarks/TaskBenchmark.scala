package monix.benchmarks

import java.util.concurrent.TimeUnit
import monix.tasks.{Task => MonixTask}
import scalaz.concurrent.{Task => ScalazTask}
import org.openjdk.jmh.annotations._
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration

/*
 * Sample run:
 *
 *     sbt "benchmarks/jmh:run -i 10 -wi 10 -f 1 -t 1 monix.benchmarks.TaskBenchmark"
 *
 * Which means "10 iterations" "5 warmup iterations" "1 fork" "1 thread".
 * Please note that benchmarks should be usually executed at least in
 * 10 iterations (as a rule of thumb), but more is better.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TaskBenchmark {
  val count = 1000000

  @Benchmark
  def doFlatMapMonix(): Long = {
    def sum(n: Int, acc: Long = 0): MonixTask[Long] = {
      if (n == 0) MonixTask(acc) else
        MonixTask(n).flatMap(x => sum(x-1, acc + x))
    }

    Await.result(sum(count).runAsync, Duration.Inf)
  }

  @Benchmark
  def doFlatMapScalazSync(): Long = {
    def sum(n: Int, acc: Long = 0): ScalazTask[Long] = {
      if (n == 0) ScalazTask.delay(acc) else
        ScalazTask.delay(n).flatMap(x => sum(x-1, acc + x))
    }

    sum(count).unsafePerformSync
  }

  @Benchmark
  def doFlatMapScalazForkedSlow(): Long = {
    def sum(n: Int, acc: Long = 0): ScalazTask[Long] = {
      if (n == 0) ScalazTask(acc) else
        ScalazTask(n).flatMap(x => sum(x-1, acc + x))
    }

    sum(count).unsafePerformSync
  }

  @Benchmark
  def doFlatMapScalazForkedFast(): Long = {
    def sum(n: Int, depth: Int, acc: Long = 0): ScalazTask[Long] = {
      if (n == 0)
        ScalazTask(acc)
      else if (depth >= 1000)
        ScalazTask(n).flatMap(x => sum(x-1, 0, acc + x))
      else
        ScalazTask.delay(n).flatMap(x => sum(x-1, depth+1, acc + x))
    }

    sum(count, 0).unsafePerformSync
  }

  @Benchmark
  def doFlatMapFuture(): Long = {
    def sum(n: Int, acc: Long = 0): Future[Long] = {
      if (n == 0) Future(acc) else
        Future(n).flatMap(x => sum(x-1, acc + x))
    }

    Await.result(sum(count), Duration.Inf)
  }
}
