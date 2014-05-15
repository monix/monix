package monifu.benchmarks

import scala.reflect.ClassTag
import com.google.caliper.SimpleBenchmark

trait SimpleScalaBenchmark extends SimpleBenchmark {
  // helper method to keep the actual benchmarking methods a bit cleaner
  // your code snippet should always return a value that cannot be "optimized away"
  def repeat[@specialized A](reps: Int)(snippet: => A) = {
    val zero = 0.asInstanceOf[A] // looks weird but does what it should: init w/ default value in a fully generic way
    var i = 0
    var result = zero
    while (i < reps) {
      val res = snippet 
      if (res != zero) result = res // make result depend on the benchmarking snippet result 
      i = i + 1
    }
    result
  }
}

object SimpleScalaBenchmark {
  def createObjects[T : ClassTag](total: Int)(cb: => T): Array[T] = {
    val result = new Array[T](total)
    var idx = 0
    while (idx < total) {
      result(idx) = cb
      idx += 1
    }
    result
  }

  def startThread(name: String)(cb: => Unit): Thread = {
    val th = new Thread(new Runnable {
      def run(): Unit = cb
    })

    th.setName(s"benchmark-thread-$name")
    th.setDaemon(true)
    th.start()
    th
  }
}