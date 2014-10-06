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

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.{BufferPolicy, Observable}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Just a sample created for quickly sketching and measuring
 * performance with external profiling.
 */
object SampleForProfiling extends App {
  Console.readLine()

  val f = Observable.from(0 until 1000000)
    .map(x => Observable.from(x until (x + 2)))
    .merge(BufferPolicy.BackPressured(2000))
    .bufferTimed(1.second)
    .foldLeft(0L)((sum, seq) => sum + seq.sum)
    .asFuture

  val result = Await.result(f, Duration.Inf)
  println(s"Result: $result")

  Console.readLine()
}
