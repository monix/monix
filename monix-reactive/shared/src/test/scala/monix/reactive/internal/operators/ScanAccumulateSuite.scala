package monix.reactive.internal.operators

import cats.laws._
import cats.laws.discipline._
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero
import scala.util.Failure

object ScanAccumulateSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .scanAccumulate(0L)((acc, elem) => (acc + elem, acc * elem))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .scan(0L)(_ + _)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int) = {
    (0 until sourceCount)
      .map(c => (0 until c).map(_.toLong).sum * c.toLong)
      .sum
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).scanAccumulate(0L) { (acc, elem) =>
      if (elem == sourceCount - 1)
        throw ex
      else
        (acc + elem, acc * elem)
    }

    Sample(o, count(sourceCount - 1), sum(sourceCount - 1), Zero, Zero)
  }

  override def cancelableObservables() = {
    val sample = Observable
      .range(1, 100)
      .delayOnNext(1.second)
      .scanAccumulate(0L)((acc, elem) => (acc + elem, acc * elem))

    Seq(Sample(sample, 0, 0, 0.seconds, 0.seconds))
  }

  test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1, 2, 3, 4).scanAccumulate[Int, Int](throw ex)((acc, elem) => (acc + elem, acc * elem))
    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
