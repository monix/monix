package monifu.benchmarks.locks

import monifu.concurrent.locks.NaiveSpinLock
import monifu.benchmarks.SimpleScalaBenchmark
import com.google.caliper.Param
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class FuturesWithLockBenchmark extends SimpleScalaBenchmark {
  class Box[T](val value: T)
  val gate = NaiveSpinLock()

  val nrOfIterations: Int = 1000000

  @Param(Array("1", "2", "3", "5", "8", "13", "21"))
  val concurrency: Int = 0

  def timeFibonacci_SpinLock(reps: Int) = repeat(reps) {
    var (a,b) = (1L,1L)

    def loop(counter: Int): Future[Long] = {
      val f = Future {
        gate.lock {
          val tmp = a
          a = b
          b = tmp + a
        }
      }

      if (counter > 0)
        f.flatMap(_ => loop(counter - 1))
      else
        Future.successful(gate.lock(b))
    }

    val futures = for (i <- 0 until concurrency) yield loop(nrOfIterations)
    Await.result(Future.sequence(futures), 60.seconds)
  }

  def timeFibonacci_IntrinsicLock(reps: Int) = repeat(reps) {
    var (a,b) = (1L,1L)

    def loop(counter: Int): Future[Long] = {
      val f = Future {
        gate.synchronized {
          val tmp = a
          a = b
          b = tmp + a
        }
      }

      if (counter > 0)
        f.flatMap(_ => loop(counter - 1))
      else
        Future.successful(gate.lock(b))
    }

    val futures = for (i <- 0 until concurrency) yield loop(nrOfIterations)
    Await.result(Future.sequence(futures), 60.seconds)
  }
}

object FuturesWithLockBenchmark {
  // main method for IDEs, from the CLI you can also run the caliper Runner directly
  // or simply use SBTs "run" action
  def main(args: Array[String]) {
    import com.google.caliper.Runner
    // we simply pass in the CLI args,
    // we could of course also just pass hardcoded arguments to the caliper Runner
    Runner.main(classOf[FuturesWithLockBenchmark], args: _*)
  }
}