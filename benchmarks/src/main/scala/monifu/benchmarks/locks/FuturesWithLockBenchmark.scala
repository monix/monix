package monifu.benchmarks.locks

import monifu.benchmarks.SimpleScalaBenchmark
import com.google.caliper.Param
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import monifu.concurrent.locks.SpinLock
import java.util.concurrent.locks.ReentrantLock
import monifu.concurrent.locks.Lock.Extensions


class FuturesWithLockBenchmark extends SimpleScalaBenchmark {
  class Box[T](val value: T)
  val nrOfIterations: Int = 100000

  @Param(Array("1", "2", "3", "5", "8", "13", "21"))
  val concurrency: Int = 0

  def timeFibonacci_SpinLock(reps: Int) = repeat(reps) {
    val gate = new SpinLock()
    var (a,b) = (1L,1L)

    def loop(counter: Int): Future[Long] = {
      val f = Future {
        val curr = (0 until counter).sum + gate.enter(b)
        val next = gate.enter {
          val tmp = a
          a = b
          b = tmp + a
          b
        }
        curr + next + 0.until(counter, 2).sum
      }

      if (counter > 0)
        f.flatMap(x => loop(counter - 1).map(_ + x))
      else
        f
    }

    val futures = for (i <- 0 until concurrency) yield loop(nrOfIterations)
    Await.result(Future.sequence(futures), 60.seconds)
  }

  def timeFibonacci_IntrinsicLock(reps: Int) = repeat(reps) {
    val ilock = new AnyRef
    var (a,b) = (1L,1L)

    def loop(counter: Int): Future[Long] = {
      val f = Future {
        val curr = (0 until counter).sum + ilock.synchronized(b)
        val next = ilock.synchronized {
          val tmp = a
          a = b
          b = tmp + a
          b
        }
        curr + next + 0.until(counter, 2).sum
      }

      if (counter > 0)
        f.flatMap(x => loop(counter - 1).map(_ + x))
      else
        f
    }

    val futures = for (i <- 0 until concurrency) yield loop(nrOfIterations)
    Await.result(Future.sequence(futures), 60.seconds)
  }

  def timeFibonacci_JavaLock(reps: Int) = repeat(reps) {
    val javaGate = new ReentrantLock()
    var (a,b) = (1L,1L)

    def loop(counter: Int): Future[Long] = {
      val f = Future {
        val curr = (0 until counter).sum + javaGate.enter(b)
        val next = javaGate.enter {
          val tmp = a
          a = b
          b = tmp + a
          b
        }
        curr + next + 0.until(counter, 2).sum
      }

      if (counter > 0)
        f.flatMap(x => loop(counter - 1).map(_ + x))
      else
        f
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