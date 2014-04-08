package monifu.concurrent.locks

import org.scalatest.FunSuite
import monifu.concurrent.Runnable
import java.util.concurrent.CountDownLatch

class NaiveSpinLockTest extends FunSuite {
  test("reentrancy") {
    val lock = NaiveSpinLock()
    var effect = "hello"

    lock.acquire {
      lock.acquire {
        lock.acquire(effect = "world")
      }
    }

    assert(effect === "world")
  }

  test("mutex exclusion") {
    val producerStarted = new CountDownLatch(1)
    val consumerStarted = new CountDownLatch(1)
    val start = new CountDownLatch(1)
    val lock = NaiveSpinLock()
    var effect = "hello"
    var reading = ""

    def startProducer() = startThread("producer") {
      lock.acquire {
        producerStarted.countDown()
        start.await()
        effect = "world"
      }
    }

    def startConsumer() = startThread("consumer") {
      consumerStarted.countDown()
      lock.acquire {
        reading = effect.map(_.toUpper)
      }
    }

    startProducer()
    producerStarted.await()
    val consumer = startConsumer()
    consumerStarted.await()

    Thread.sleep(10)
    start.countDown()
    consumer.join()

    assert(reading === "WORLD")
  }

  def startThread(name: String)(cb: => Unit) = {
    val th = new Thread(Runnable(cb))
    th.setName(s"monifu-reentrant-lock-test-$name")
    th.setDaemon(true)
    th.start()
    th
  }
}
