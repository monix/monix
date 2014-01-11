package monifu.concurrent.locks

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.{CountDownLatch, TimeUnit}
import concurrent._
import concurrent.ExecutionContext.Implicits.global
import monifu.concurrent.atomic.Atomic


@RunWith(classOf[JUnitRunner])
class NonBlockingReadWriteLockTest extends FunSuite {
  val lock = NonBlockingReadWriteLock()

  test("basic read holding write") {
    @volatile var value = 3
    @volatile var readValue = -1

    val awaitStart = new CountDownLatch(1)
    val startReadSignal = new CountDownLatch(1)
    val startWriteSignal = new CountDownLatch(1)
    val doneSignal = new CountDownLatch(2)

    def readWorker() = Future {
      lock.readLock {
        awaitStart.countDown()
        startWriteSignal.countDown()
        startReadSignal.await()
        readValue = value
      }
      doneSignal.countDown()
    }

    def writeWorker() = Future {
      startWriteSignal.await(5, TimeUnit.SECONDS)
      lock.writeLock(value = 10)
      doneSignal.countDown()
    }

    readWorker()
    writeWorker()

    awaitStart.await(5, TimeUnit.SECONDS)
    startReadSignal.countDown()
    doneSignal.await(5, TimeUnit.SECONDS)

    assert(value === 10)
    assert(readValue === 3)
  }

  test("reads are concurrent") {
    val latch = new CountDownLatch(2)
    val active = Atomic(0)
    val futureWait = new CountDownLatch(1)

    def createFuture = Future {
      lock.readLock {
        latch.countDown
        active.increment
        futureWait.await(5, TimeUnit.SECONDS)
      }
    }

    createFuture; createFuture
    latch.await(5, TimeUnit.SECONDS)    
    assert(active.get === 2)
    futureWait.countDown
  }

  test("synchronization for writes works") {
    val latch = new CountDownLatch(200)
    val startSignal = new CountDownLatch(1)
    val readCond = Atomic(true)
    val readsNr = Atomic(0)

    var z = 0
    var a = 1
    var b = 1

    def createReader = {
      val th = new Thread(new Runnable {
        def run: Unit = {
          latch.countDown
          startSignal.await(5, TimeUnit.SECONDS)
          lock.readLock {
            readCond.transform(_ && (z + a == b))
            readsNr.increment
          }
        }
      })

      th.start()
      th
    }

    def createWriter: Thread = {
      val th = new Thread(new Runnable {
        def run: Unit = {
          latch.countDown
          startSignal.await(5, TimeUnit.SECONDS)
          lock.writeLock {
            z = a
            a = b
            b = z + a
          }
        }
      })

      th.start()
      th
    }

    val threads = 
      (0 until 100).map(_ => createWriter) ++ 
      (0 until 100).map(_ => createReader)

    latch.await(5, TimeUnit.SECONDS)
    startSignal.countDown

    threads.foreach(_.join)
    assert(b === 1445263496)
    assert(readCond.get === true)
    assert(readsNr.get === 100)
  }
}