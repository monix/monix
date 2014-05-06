package monifu.benchmarks.locks

import monifu.benchmarks.SimpleScalaBenchmark
import com.google.caliper.Param
import monifu.concurrent.locks.NaiveSpinLock

class FibonacciLockBenchmark extends SimpleScalaBenchmark {
  import SimpleScalaBenchmark._

  @Param(Array("1", "2", "3", "5","8", "13", "21", "50", "100"))
  val nrOfThreads: Int = 0

  class Box[T](val value: T)

  def timeFibonacci_SpinLock_Reentrant(reps: Int) = repeat(reps) {
    val gate = new NaiveSpinLock()
    var (a,b) = (1L,1L)

    var i = 0
    val threads =
      createObjects(nrOfThreads) {
        val th = startThread("increment-" + i) {
          for (i <- 0 until 100)
            gate.enter {
              val tmp = a
              a = b
              b = tmp + a

              for (k <- 0 until 100) {
                gate.enter {
                  val tmp = a
                  a = b
                  b = tmp + a
                }
              }
            }
        }

        i += 1
        th
      }

    i = 0
    while (i < nrOfThreads) { threads(i).join(); i += 1}
    Some(b)
  }

  def timeFibonacci_IntrinsicLock_Reentrant(reps: Int) = repeat(reps) {
    val gate = new NaiveSpinLock()
    var (a,b) = (1L,1L)

    var i = 0
    val threads =
      createObjects(nrOfThreads) {
        val th = startThread("increment-" + i) {
          for (i <- 0 until 100)
            gate.synchronized {
              val tmp = a
              a = b
              b = tmp + a

              for (k <- 0 until 100) {
                gate.synchronized {
                  val tmp = a
                  a = b
                  b = tmp + a
                }
              }
            }
        }

        i += 1
        th
      }

    i = 0
    while (i < nrOfThreads) { threads(i).join(); i += 1}
    Some(b)
  }

  def timeFibonacci_SpinLock_NonReentrant(reps: Int) = repeat(reps) {
    val gate = new NaiveSpinLock()
    var (a,b) = (1L,1L)

    var i = 0
    val threads =
      createObjects(nrOfThreads) {
        val th = startThread("increment-" + i) {
          for (i <- 0 until 100)
            gate.enter {
              val tmp = a
              a = b
              b = tmp + a

              for (k <- 0 until 1000) {
                val tmp = a
                a = b
                b = tmp + a
              }
            }
        }

        i += 1
        th
      }

    i = 0
    while (i < nrOfThreads) { threads(i).join(); i += 1}
    Some(b)
  }

  def timeFibonacci_IntrinsicLock_NonReentrant(reps: Int) = repeat(reps) {
    val gate = new NaiveSpinLock()
    var (a,b) = (1L,1L)

    var i = 0
    val threads =
      createObjects(nrOfThreads) {
        val th = startThread("increment-" + i) {
          for (i <- 0 until 100)
            gate.synchronized {
              val tmp = a
              a = b
              b = tmp + a

              for (k <- 0 until 1000) {
                val tmp = a
                a = b
                b = tmp + a
              }
            }
        }

        i += 1
        th
      }

    i = 0
    while (i < nrOfThreads) { threads(i).join(); i += 1}
    Some(b)
  }
}

object FibonacciLockBenchmark {
  // main method for IDEs, from the CLI you can also run the caliper Runner directly
  // or simply use SBTs "run" action
  def main(args: Array[String]) {
    import com.google.caliper.Runner
    // we simply pass in the CLI args,
    // we could of course also just pass hardcoded arguments to the caliper Runner
    Runner.main(classOf[FibonacciLockBenchmark], args: _*)
  }
}