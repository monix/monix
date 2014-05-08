package monifu.benchmarks.locks

import monifu.benchmarks.SimpleScalaBenchmark
import com.google.caliper.Param
import monifu.concurrent.locks.{SpinLock2, SpinLock}
import monifu.concurrent.locks.Lock.Extensions


class SpinLockOptimize extends SimpleScalaBenchmark {
  import SimpleScalaBenchmark._

  class Box[T](val value: T)
  val nrOfIterations: Int = 5000

  @Param(Array("1", "1", "1", "2", "3", "5"))
  val concurrency: Int = 0

  def timeSpinLock_1(reps: Int) = repeat(reps) {
    val gate = new SpinLock()
    var iterations = 0
    var (a,b) = (0,1)

    val threads = createObjects(concurrency) {
      startThread("spin-lock-thread") {
        var continue = true
        while (continue)
          gate.enter {
            if (iterations < nrOfIterations / concurrency) {
              val tmp = a; a = b; b = a + tmp
              iterations += 1
            }
            else
              continue = false
          }
      }
    }

    threads.foreach(_.join())
    Some(b)
  }

  def timeSpinLock_2(reps: Int) = repeat(reps) {
    val gate = new SpinLock2()
    var iterations = 0
    var (a,b) = (0,1)

    val threads = createObjects(concurrency) {
      startThread("spin-lock-thread") {
        var continue = true
        while (continue)
          gate.enter {
            if (iterations < nrOfIterations / concurrency) {
              val tmp = a; a = b; b = a + tmp
              iterations += 1
            }
            else
              continue = false
          }
      }
    }

    threads.foreach(_.join())
    Some(b)
  }
}

object SpinLockOptimize {
  // main method for IDEs, from the CLI you can also run the caliper Runner directly
  // or simply use SBTs "run" action
  def main(args: Array[String]) {
    import com.google.caliper.Runner
    // we simply pass in the CLI args,
    // we could of course also just pass hardcoded arguments to the caliper Runner
    Runner.main(classOf[SpinLockOptimize], args: _*)
  }
}