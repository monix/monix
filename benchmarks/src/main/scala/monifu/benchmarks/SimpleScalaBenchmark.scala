/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
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