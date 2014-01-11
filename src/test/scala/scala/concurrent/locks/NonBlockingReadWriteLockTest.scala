package monifu.concurrent.locks

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.CountDownLatch
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
      startWriteSignal.await()
      lock.writeLock(value = 10)
      doneSignal.countDown()
    }

    readWorker()
    writeWorker()

    awaitStart.await()
    startReadSignal.countDown()
    doneSignal.await()

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
        futureWait.await
      }
    }

    createFuture; createFuture
    latch.await    
    assert(active.get === 2)
    futureWait.countDown
  }

  test("synchronization for writes works") {
    val latch = new CountDownLatch(200)
    val startSignal = new CountDownLatch(1)
    val readCond = Atomic(true)

    var z = 0
    var a = 1
    var b = 1

    def createReader = {
      val th = new Thread(new Runnable {
        def run: Unit = {
          latch.countDown
          startSignal.await
          lock.readLock {
            readCond.transform(_ && (z + a == b))
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
          startSignal.await
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

    latch.await
    startSignal.countDown

    threads.foreach(_.join)
    assert(b === 1445263496)
    assert(readCond.get === true)
  }
}