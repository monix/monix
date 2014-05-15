package monifu.rx.sync.subjects

import scala.scalajs.test.JasmineTest
import monifu.concurrent.cancelables.BooleanCancelable

object PublishSubjectTest extends JasmineTest {
  describe("PublishSubject") {
    it("should propagate onNext") {
      val subject = PublishSubject[Int]()

      var sum = 0
      subject.filter(_ % 2 == 0).map(_ * 2).subscribeUnit(x => sum += x)
      subject.filter(_ % 2 == 1).map(_ * 2).subscribeUnit(x => sum += x)

      for (i <- 1 to 10)
        subject.onNext(i)
      subject.onCompleted()

      expect(sum).toBe(5 * 11 * 2)
    }

    it("should cancel subscriptions when terminated") {
      val subject = PublishSubject[Int]()
      val sub1 = subject.filter(_ % 2 == 0).map(_ * 2).subscribeUnit(_ => ())
      val sub2 = subject.filter(_ => true).subscribeUnit(_ => ())

      subject.onNext(1)
      subject.onCompleted()

      expect(sub1.asInstanceOf[BooleanCancelable].isCanceled).toBe(true)
      expect(sub2.asInstanceOf[BooleanCancelable].isCanceled).toBe(true)
    }
  }
}
