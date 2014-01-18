package monifu.util

import scala.concurrent.duration._

package performance {
  case class BenchmarkResult(
    total: Long,
    mean: Long,
    median: Long,
    meanPercentile99: Long,
    meanPercentile98: Long,
    meanPercentile95: Long,
    meanPercentile90: Long,
    meanPercentile85: Long,
    meanPercentile70: Long
  )
}

package object performance {
  def benchmark(warmupRunsNr: Int, runsNr: Int)(cb: => Unit): BenchmarkResult = {
    require(warmupRunsNr >= 0, "warmupRunsNr must be a positive integer")
    require(runsNr > 0, "runsNr must be a strictly positive integer")

    def meanOf(runs: Seq[Long], percentile: Int) = {
      require(runs.size > 0)
      val keepNr = math.round(runs.size * percentile / 100.0).toInt
      val nrs = runs.sorted.take(keepNr)
      nrs.map(x => x.toDouble / nrs.size).sum.toLong
    }

    def medianOf(runs: Seq[Long]) = {
      require(runs.size > 0)
      runs.sorted.apply(runs.size / 2)
    }

    // warm-ups

    var i = 0
    while (i < warmupRunsNr) { 
      System.nanoTime
      cb; i += 1 
    }

    // starting benchmark 
    
    var runs = Vector.empty[Long]
    i = 0

    while (i < runsNr) {
      val clockStart = System.nanoTime
      cb
      val clockEnd = System.nanoTime

      runs = runs :+ (clockEnd - clockStart)
      i += 1
    }

    BenchmarkResult(
      total = runs.sum,
      mean = meanOf(runs, 100),
      median = medianOf(runs),
      meanPercentile99 = meanOf(runs, 99),
      meanPercentile98 = meanOf(runs, 98),
      meanPercentile95 = meanOf(runs, 95),
      meanPercentile90 = meanOf(runs, 90),
      meanPercentile85 = meanOf(runs, 85),
      meanPercentile70 = meanOf(runs, 70)
    )
  }

  def benchmarkPrettyPrint(warmupRunsNr: Int, runsNr: Int)(cb: => Unit): Unit = {
    def toSecs(nanos: Long) =
      nanos / 1000000000.0

    val results = benchmark(warmupRunsNr, runsNr)(cb)

    println("\n         |     Nanoseconds | Seconds")
    println("------------------------------------")
    println(f"Total    | ${results.total}%15d | ${toSecs(results.total)}%7.2f")
    println("------------------------------------")
    println(f"Mean     | ${results.mean}%15d | ${toSecs(results.mean)}%7.2f")
    println("------------------------------------")
    println(f"Median   | ${results.median}%15d | ${toSecs(results.median)}%7.2f")
    println("------------------------------------")
    println(f"Mean 99%% | ${results.meanPercentile99}%15d | ${toSecs(results.meanPercentile99)}%7.2f")
    println(f"Mean 98%% | ${results.meanPercentile98}%15d | ${toSecs(results.meanPercentile98)}%7.2f")
    println(f"Mean 95%% | ${results.meanPercentile95}%15d | ${toSecs(results.meanPercentile95)}%7.2f")
    println(f"Mean 90%% | ${results.meanPercentile90}%15d | ${toSecs(results.meanPercentile90)}%7.2f")
    println(f"Mean 85%% | ${results.meanPercentile85}%15d | ${toSecs(results.meanPercentile85)}%7.2f")
    println(f"Mean 70%% | ${results.meanPercentile70}%15d | ${toSecs(results.meanPercentile70)}%7.2f")
  }
}