package monifu.benchmarks.misc

import monifu.benchmarks.SimpleScalaBenchmark
import com.google.caliper.Param
import monifu.concurrent.ThreadLocal
import monifu.concurrent.atomic.padded.Atomic


class ThreadLocalBenchmark extends SimpleScalaBenchmark {
  import SimpleScalaBenchmark._

  val nrOfIterations: Int = 50000
  class BoxedInt(v: Int) { val int = Atomic(v) }
  class BoxedThreadLocal(v:  Int) { val int = ThreadLocal(v) }

  @Param(Array("1", "2", "3", "4", "5"))
  val runNumber: Int = 0

  def timeLocal(reps: Int) = repeat(reps) {
    val local = new BoxedInt(0)
    val threads = createObjects(3) {
      startThread("local-inc") {
        var idx = 0

        while (idx < nrOfIterations) {
          local.int.lazySet(local.int.get + 1)
          idx += 1
        }
      }
    }

    threads.foreach(_.join())
    Some(local.int)
  }

  def timeThreadLocal(reps: Int) = repeat(reps) {
    val local = new BoxedThreadLocal(0)

    val threads = createObjects(3) {
      startThread("local-inc") {
        var idx = 0
        while (idx < nrOfIterations) {
          local.int.set(local.int.get() + 1)
          idx += 1
        }
      }
    }

    threads.foreach(_.join())
    Some(local.int.get())
  }
}

object ThreadLocalBenchmark {
  // main method for IDEs, from the CLI you can also run the caliper Runner directly
  // or simply use SBTs "run" action
  def main(args: Array[String]) {
    import com.google.caliper.Runner
    // we simply pass in the CLI args,
    // we could of course also just pass hardcoded arguments to the caliper Runner
    Runner.main(classOf[ThreadLocalBenchmark], args: _*)
  }
}