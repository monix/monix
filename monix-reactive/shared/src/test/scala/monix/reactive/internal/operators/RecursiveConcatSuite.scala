package monix.reactive.internal.operators

import monix.reactive.Observable

import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object RecursiveConcatSuite extends BaseOperatorSuite {
  def range(from: Long, until: Long): Observable[Long] =
    Observable.defer {
      Observable.now(from) ++ (
        if (from + 1 < until) range(from + 1, until)
        else Observable.empty
      )
    }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount - 1) / 2

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    if (sourceCount <= 1) None else Some {
      val o = range(0, sourceCount)
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None
  def cancelableObservables() = Seq.empty

  test("stack safety") { implicit s =>
    val count = 10000
    val f = range(0, count).sumL.runAsync; s.tick()
    assertEquals(f.value, Some(Success(count.toLong * (count - 1) / 2)))
  }
}
