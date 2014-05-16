package monifu.rx

object SyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[Observable](Observable.Builder, isAsync = false) {

  describe("Observable.zip") {
    it("should work") {
      val obs1 = Observable.fromTraversable(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.fromTraversable(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.fromTraversable(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.fromTraversable(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      var list = Seq.empty[(Long, Long, Long, Long)]
      finalObs.subscribeUnit { l => list = l }

      val condition = list.forall { case (a,b,c,d) => a == b && b == c && c == d }
      expect(condition).toBe(true)
    }
  }
}