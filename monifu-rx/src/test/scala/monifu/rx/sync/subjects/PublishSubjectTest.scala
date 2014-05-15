package monifu.rx.sync.subjects

import org.scalatest.FunSuite
import monifu.concurrent.cancelables.BooleanCancelable

class PublishSubjectTest extends FunSuite {
  test("propagate onNext") {
    val subject = PublishSubject[Int]()

    var sum = 0
    subject.filter(_ % 2 == 0).map(_ * 2).subscribeUnit(x => sum += x)
    subject.filter(_ % 2 == 1).map(_ * 2).subscribeUnit(x => sum += x)

    for (i <- 1 to 10)
      subject.onNext(i)
    subject.onCompleted()

    assert(sum === 5 * 11 * 2)
  }

  test("cancel subscriptions onTerminated") {
    val subject = PublishSubject[Int]()
    val sub1 = subject.filter(_ % 2 == 0).map(_ * 2).subscribeUnit(_ => ())
    val sub2 = subject.filter(_ => true).subscribeUnit(_ => ())

    subject.onNext(1)
    subject.onCompleted()

    assert(sub1.asInstanceOf[BooleanCancelable].isCanceled === true)
    assert(sub2.asInstanceOf[BooleanCancelable].isCanceled === true)
  }
}
