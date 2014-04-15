package monifu.rx.sync

import scala.concurrent.Await
import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class SyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[Observable](Observable.Builder) {
  
  describe("Observable.zip") {
    it("should work") {
      val obs1 = Observable.fromTraversable(0 until 10).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 10).map(_ * 2).map(_.toLong)

      val zipped = obs1.zip(obs2)

      val finalObs = zipped.foldLeft(Seq.empty[(Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 1.second)

      assert(result === Some(0.until(10,2).map(x => (x,x))))
    }

    it("should work in four") {
      val obs1 = Observable.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 1.second)

      assert(result === Some(0.until(20,2).map(x => (x,x,x,x))))
    }
  }
}