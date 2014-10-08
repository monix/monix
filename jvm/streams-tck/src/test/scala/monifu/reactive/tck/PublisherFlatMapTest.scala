package monifu.reactive.tck

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Observable
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import org.scalatest.testng.TestNGSuiteLike

class PublisherFlatMapTest
  extends PublisherVerification[Int](new TestEnvironment(1000), 1000) with TestNGSuiteLike {

  def createPublisher(elements: Long): Publisher[Int] = {
    val obs = Observable.repeat(1).flatMap(x => Observable.unit(x))

    if (elements == Long.MaxValue)
      obs.publisher
    else
      obs.take(elements.toInt).publisher
  }

  def createErrorStatePublisher(): Publisher[Int] = {
    Observable.error(new RuntimeException).publisher[Int]
  }
}