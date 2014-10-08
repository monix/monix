package monifu.reactive.tck

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Observable
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import org.scalatest.testng.TestNGSuiteLike

class PublisherSimpleRepeatTest
  extends PublisherVerification[Int](new TestEnvironment(1000), 1000) with TestNGSuiteLike {

  def createPublisher(elements: Long): Publisher[Int] = {
    if (elements == Long.MaxValue)
      Observable.repeat(1).publisher
    else
      Observable.repeat(1).take(elements.toInt).publisher
  }

  def createErrorStatePublisher(): Publisher[Int] = {
    Observable.error(new RuntimeException).publisher[Int]
  }
}
