package monifu.concurrent.locks

import org.scalatest.FunSuite
import java.util.concurrent.{CountDownLatch, TimeUnit}
import monifu.concurrent.atomic.Atomic

class NaiveReadWriteLockTest extends FunSuite {
  val lock = new NaiveReadWriteLock

  test("basic read holding write") {
    @volatile var value = 3
    @volatile var readValue = -1

    val awaitStart = new CountDownLatch(1)
    val startReadSignal = new CountDownLatch(1)
    val startWriteSignal = new CountDownLatch(1)
    val doneSignal = new CountDownLatch(2)

    def readWorker() = startThread {
      awaitStart.countDown()
      lock.readLock {
        startWriteSignal.countDown()
        startReadSignal.await()
        readValue = value
      }
    }

    def writeWorker() = startThread {
      startWriteSignal.await(5, TimeUnit.SECONDS)
      lock.writeLock(value = 10)
      doneSignal.countDown()
    }

    val r = readWorker()
    val w = writeWorker()

    awaitStart.await(5, TimeUnit.SECONDS)
    startReadSignal.countDown()
    doneSignal.await(5, TimeUnit.SECONDS)

    assert(value === 10)
    assert(readValue === 3)

    r.join(); w.join()
  }

  test("reads are concurrent") {
    val latch = new CountDownLatch(2)
    val active = Atomic(0)
    val futureWait = new CountDownLatch(1)

    def createThread = startThread {
      lock.readLock {
        active.increment()
        latch.countDown()
        futureWait.await(5, TimeUnit.SECONDS)
      }
      active.decrement()
    }

    val t1 = createThread
    val t2 = createThread

    latch.await(5, TimeUnit.SECONDS)
    assert(active.get === 2)

    futureWait.countDown()
    t1.join(); t2.join()
    assert(active.get === 0)
  }

  test("synchronization works") {
    val latch = new CountDownLatch(600)
    val startSignal = new CountDownLatch(1)
    val readCond = Atomic(true)
    val readsNr = Atomic(0)

    // generating Fibonacci stuff
    var z = 0
    var a = 1
    var b = 1

    def createReader = startThread {
      latch.countDown()
      startSignal.await(5, TimeUnit.SECONDS)
      lock.readLock {
        readCond.transform(_ && (z + a == b))
        readsNr.increment()
      }
    }

    def createWriter = startThread {
      latch.countDown()
      startSignal.await(5, TimeUnit.SECONDS)
      lock.writeLock {
        z = a
        a = b
        b = z + a
      }
    }

    val threads = 
      (0 until 100).map(_ => createWriter) ++ 
      (0 until 500).map(_ => createReader)

    latch.await(5, TimeUnit.SECONDS)
    startSignal.countDown()

    threads.foreach(_.join)
    assert(b === 1445263496)
    assert(readCond.get === true)
    assert(readsNr.get === 500)
  }

  test("re-entrance logic works") {
    val latch = new CountDownLatch(100)
    val startSignal = new CountDownLatch(1)
    val readCond = Atomic(true)
    val readsNr = Atomic(0)

    // generating Fibonacci stuff
    var z = 0
    var a = 1
    var b = 1

    def createWriter = startThread {
      latch.countDown()
      startSignal.await(5, TimeUnit.SECONDS)
      lock.readLock {
        readsNr.increment()
        var a2 = a; var b2 = b; var z2 = z
        lock.writeLock {
          z = a
          a = b
          b = z + a
          a2 = a; b2 = b; z2 = z
        }
        readCond.transform(_ && (z2 + a2 == b2))
      }
    }

    val threads = (0 until 100).map(_ => createWriter).toList 

    latch.await()
    startSignal.countDown()

    threads.foreach(_.join)
    assert(b === 1445263496)
    assert(readCond.get === true)
    assert(readsNr.get === 100)
  }

  def startThread(cb: => Unit) = {
    val th = new Thread(new Runnable { def run() = {
      try cb catch {
        case ex: Throwable =>
          println(ex.toString)
          throw ex
      }
    }})
    th.start(); th
  }
}