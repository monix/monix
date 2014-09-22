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
    .buffer(1.second)
    .foldLeft(0L)((sum, seq) => sum + seq.sum)
    .asFuture

  val result = Await.result(f, Duration.Inf)
  println(s"Result: $result")

  Console.readLine()
}
