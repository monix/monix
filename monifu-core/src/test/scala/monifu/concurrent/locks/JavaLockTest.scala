package monifu.concurrent.locks

import org.scalatest.FunSuite
import monifu.concurrent.Runnable
import java.util.concurrent.{TimeUnit, CountDownLatch}
import concurrent.duration._
import monifu.concurrent.atomic.Atomic
import scala.collection.mutable
import java.util.Date
import Lock.Extensions


class JavaLockTest extends FunSuite {
  @volatile var barrier = false
  val lock = new java.util.concurrent.locks.ReentrantLock()

  test("re-entrance") {
    var effect = "hello"

    lock.enter {
      lock.enter {
        lock.enter { effect = "world" }
      }
    }

    assert(effect === "world")
  }

  test("mutex exclusion") {
    val producerStarted = new CountDownLatch(1)
    val consumerStarted = new CountDownLatch(1)
    val start = new CountDownLatch(1)
    val end = new CountDownLatch(1)
    var effect = "hello"
    var reading = ""

    def startProducer() = startThread("producer") {
      lock.enter {
        producerStarted.countDown()
        start.await()
        effect = "world"
      }
    }

    def startConsumer() = startThread("consumer") {
      consumerStarted.countDown()
      lock.enter {
        reading = effect.map(_.toUpper)
      }
      end.countDown()
    }

    startProducer()
    producerStarted.await()
    startConsumer()
    consumerStarted.await()

    Thread.sleep(10)
    start.countDown()
    end.await(1, TimeUnit.SECONDS)

    assert(reading === "WORLD")
  }

  test("lockInterruptibly()") {
    lock.enterInterruptibly {
      val started = new CountDownLatch(1)
      val latch = new CountDownLatch(1)
      val th = startThread("test") {
        try {
          started.countDown()
          lock.lockInterruptibly()
        }
        catch {
          case _: InterruptedException =>
            latch.countDown()
        }
      }

      started.await(1, TimeUnit.SECONDS)
      th.interrupt()
      latch.await(1, TimeUnit.SECONDS)
    }
  }

  test("lockInterruptibly() is reentrant") {
    var result = false

    lock.enterInterruptibly {
      lock.enterInterruptibly {
        result = true
      }
    }

    assert(result === true)
  }

  test("lock() cannot be interrupted") {
    val result = Atomic(false)
    val latch = new CountDownLatch(1)

    lock.enter {
      val started = new CountDownLatch(1)
      val th = startThread("test") {
        started.countDown()
        lock.enter(result.set(update = true))
        latch.countDown()
      }

      started.await(1, TimeUnit.SECONDS)
      th.interrupt()
      th
    }

    latch.await(1, TimeUnit.SECONDS)
    assert(result.get === true)
  }

  test("tryLock()") {
    var result = false

    val wasAcquired = lock.tryEnter {
      result = true
    }

    assert(wasAcquired === true)
    assert(result === true)
  }

  test("tryLock() is reentrant") {
    var result = false

    lock.tryEnter {
      lock.tryEnter {
        result = true
      }
    }

    assert(result === true)
  }

  test("tryLock() fails if another thread is holding the lock") {
    val start = new CountDownLatch(1)
    val terminate = new CountDownLatch(1)

    startThread("test-tryLock") {
      lock.enter {
        start.countDown()
        terminate.await()
      }
    }

    start.await()
    try
      assert(lock.tryLock() === false)
    finally {
      terminate.countDown()
    }
  }

  test("tryLock(time, unit)") {
    var result = false

    val wasAcquired = lock.tryEnter(1, TimeUnit.SECONDS, {
      result = true
    })

    assert(wasAcquired === true)
    assert(result === true)
  }

  test("tryLock(time, unit) is reentrant") {
    var result = false

    lock.tryEnter(1, TimeUnit.SECONDS, {
      lock.tryEnter(1, TimeUnit.SECONDS, {
        result = true
      })
    })

    assert(result === true)
  }

  test("tryLock(time, unit) can be interrupted") {
    lock.tryEnter(1, TimeUnit.SECONDS, {
      val started = new CountDownLatch(1)
      val latch = new CountDownLatch(1)
      val th = startThread("test") {
        try {
          started.countDown()
          lock.tryLock(1, TimeUnit.SECONDS)
        }
        catch {
          case _: InterruptedException =>
            latch.countDown()
        }
      }

      started.await(1, TimeUnit.SECONDS)
      th.interrupt()
      latch.await(1, TimeUnit.SECONDS)
    })
  }

  test("tryLock(time,unit) fails if another thread is holding the lock") {
    val start = new CountDownLatch(1)
    val terminate = new CountDownLatch(1)

    val th = startThread("test-tryLock") {
      lock.enter {
        start.countDown()
        terminate.await()
      }
    }

    start.await()
    try {
      val startedAt = System.currentTimeMillis()
      assert(lock.tryLock(100, TimeUnit.MILLISECONDS) === false)
      val duration = System.currentTimeMillis() - startedAt
      assert(duration >= 100, "duration must be at least 100 millis")
    }
    finally {
      terminate.countDown()
      th.join(1000)
    }
  }

  test("newCondition.await() can be interrupted") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val condition = lock.newCondition()

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        try {
          while (true)
            condition.await()
        }
        catch {
          case _: InterruptedException =>
            ended.countDown()
        }
      }
    }

    started.await(1, TimeUnit.SECONDS)
    th.interrupt()
    ended.await(1, TimeUnit.SECONDS)
  }

  test("newCondition.await() + newCondition.signal()") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        while (queue.isEmpty)
          isNonEmpty.await()
        result = queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      isNonEmpty.signal()
    }

    ended.await(1, TimeUnit.SECONDS)
    th.join(1000)
    assert(result === 1)
  }

  test("newCondition.await() works for reentrant locks") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th = startThread("test-condition-await") {
      lock.enter {
        lock.enter {
          started.countDown()
          while (queue.isEmpty)
            isNonEmpty.await()
          result = queue.dequeue()
          ended.countDown()
        }
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      isNonEmpty.signal()
    }

    ended.await(1, TimeUnit.SECONDS)
    th.join(1000)
    assert(result === 1)
  }

  test("newCondition.await() + newCondition.signalAll()") {
    val started = new CountDownLatch(2)
    val ended = new CountDownLatch(2)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th1 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.await()
        result += queue.dequeue()
        ended.countDown()
      }
    }

    val th2 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.await()
        result += queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      queue.enqueue(2)
      isNonEmpty.signalAll()
    }

    ended.await(1, TimeUnit.SECONDS)
    assert(result === 3)
    th1.join(1000); th2.join(1000)
  }

  test("newCondition.await() throws IllegalMonitorStateException if lock isn't acquired") {
    val isNonEmpty = lock.newCondition()
    intercept[IllegalMonitorStateException] {
      isNonEmpty.await()
    }
  }

  test("newCondition.awaitUninterruptibly() + newCondition.signal()") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        while (queue.isEmpty)
          isNonEmpty.awaitUninterruptibly()
        result = queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      isNonEmpty.signal()
    }

    ended.await(1, TimeUnit.SECONDS)
    th.join(1000)
    assert(result === 1)
  }

  test("newCondition.awaitUninterruptibly() + newCondition.signalAll()") {
    val started = new CountDownLatch(2)
    val ended = new CountDownLatch(2)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th1 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.awaitUninterruptibly()
        result += queue.dequeue()
        ended.countDown()
      }
    }

    val th2 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.awaitUninterruptibly()
        result += queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      queue.enqueue(2)
      isNonEmpty.signalAll()
    }

    ended.await(1, TimeUnit.SECONDS)
    assert(result === 3)
    th1.join(1000); th2.join(1000)
  }

  test("newCondition.awaitUninterruptibly() throws IllegalMonitorStateException if lock isn't acquired") {
    val isNonEmpty = lock.newCondition()
    intercept[IllegalMonitorStateException] {
      isNonEmpty.awaitUninterruptibly()
    }
  }

  test("newCondition.awaitUninterruptibly() cannot be interrupted") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    var exitCondition = false
    val condition = lock.newCondition()

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        while (!exitCondition)
          condition.awaitUninterruptibly()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    th.interrupt()
    Thread.sleep(50)

    lock.enter {
      exitCondition = true
      condition.signal()
    }

    ended.await(1, TimeUnit.SECONDS)
  }

  test("newCondition.awaitNanos() can be interrupted") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val condition = lock.newCondition()

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        try {
          while (true)
            condition.awaitNanos(TimeUnit.SECONDS.toNanos(1))
        }
        catch {
          case _: InterruptedException =>
            ended.countDown()
        }
      }
    }

    started.await(1, TimeUnit.SECONDS)
    th.interrupt()
    ended.await(1, TimeUnit.SECONDS)
  }

  test("newCondition.awaitNanos() + newCondition.signal()") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        while (queue.isEmpty)
          isNonEmpty.awaitNanos(TimeUnit.SECONDS.toNanos(1))
        result = queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      isNonEmpty.signal()
    }

    ended.await(1, TimeUnit.SECONDS)
    th.join(1000)
    assert(result === 1)
  }

  test("newCondition.awaitNanos() + newCondition.signalAll()") {
    val started = new CountDownLatch(2)
    val ended = new CountDownLatch(2)
    val isNonEmpty = lock.newCondition()
    val queue = mutable.Queue.empty[Int]
    var result = 0

    val th1 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.awaitNanos(TimeUnit.SECONDS.toNanos(1))
        result += queue.dequeue()
        ended.countDown()
      }
    }

    val th2 = startThread("test-condition-await") {
      started.countDown()
      lock.enter {
        while (queue.isEmpty)
          isNonEmpty.awaitNanos(TimeUnit.SECONDS.toNanos(1))
        result += queue.dequeue()
        ended.countDown()
      }
    }

    started.await(1, TimeUnit.SECONDS)
    lock.enter {
      queue.enqueue(1)
      queue.enqueue(2)
      isNonEmpty.signalAll()
    }

    ended.await(1, TimeUnit.SECONDS)
    assert(result === 3)
    th1.join(1000); th2.join(1000)
  }

  test("newCondition.awaitNanos() throws IllegalMonitorStateException if lock isn't acquired") {
    val isNonEmpty = lock.newCondition()
    intercept[IllegalMonitorStateException] {
      isNonEmpty.awaitNanos(TimeUnit.SECONDS.toNanos(1))
    }
  }

  test("newCondition.awaitNanos() fails after timeout") {
    val startedAt = System.currentTimeMillis()
    val condition = lock.newCondition()
    val endEvent = new CountDownLatch(1)

    startThread("await-nanos-thread") {
      lock.enter {
        var waitTime = TimeUnit.MILLISECONDS.toNanos(300)
        while (waitTime > 0) {
          waitTime = condition.awaitNanos(waitTime)
        }

        endEvent.countDown()
      }
    }

    endEvent.await(1, TimeUnit.SECONDS)
    assert(System.currentTimeMillis() - startedAt >= 300)
  }

  test("newCondition.await(time, unit)") {
    val startedAt = System.currentTimeMillis()
    val condition = lock.newCondition()
    val endEvent = new CountDownLatch(1)

    startThread("await-nanos-thread") {
      lock.enter {
        assert(condition.await(100, TimeUnit.MILLISECONDS) === false)
        endEvent.countDown()
      }
    }

    endEvent.await(1, TimeUnit.SECONDS)
    assert(System.currentTimeMillis() - startedAt >= 100)
  }

  test("newCondition.await(time, unit) can be interrupted") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val condition = lock.newCondition()

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        try {
          while (true)
            condition.await(1, TimeUnit.SECONDS)
        }
        catch {
          case _: InterruptedException =>
            ended.countDown()
        }
      }
    }

    started.await(1, TimeUnit.SECONDS)
    th.interrupt()
    ended.await(1, TimeUnit.SECONDS)
  }

  test("newCondition.awaitUntil(time, unit)") {
    val startedAt = System.currentTimeMillis()
    val condition = lock.newCondition()
    val endEvent = new CountDownLatch(1)

    startThread("await-nanos-thread") {
      lock.enter {
        val until = new Date(System.currentTimeMillis() + 300)
        assert(condition.awaitUntil(until) === false)
        endEvent.countDown()
      }
    }

    endEvent.await(1, TimeUnit.SECONDS)
    assert(System.currentTimeMillis() - startedAt >= 300)
  }

  test("newCondition.awaitUntil() can be interrupted") {
    val started = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val condition = lock.newCondition()

    val th = startThread("test-condition-await") {
      lock.enter {
        started.countDown()
        try {
          val until = new Date(System.currentTimeMillis() + 300)
          while (true)
            condition.awaitUntil(until)
        }
        catch {
          case _: InterruptedException =>
            ended.countDown()
        }
      }
    }

    started.await(1, TimeUnit.SECONDS)
    th.interrupt()
    ended.await(1, TimeUnit.SECONDS)
  }

  test("concurrent fibonacci") {
    var iterationsCount = 0
    var (a,b) = (1L,1L)

    for (i <- 0 until 10100000) {
      val tmp = a
      a = b
      b = tmp + a
    }

    val finalResult = b
    a = 1; b = 1

    val threads = for (i <- 0 until 10) yield startThread("increment-" + i) {
      for (j <- 0 until 10000) {
        lock.enter {
          iterationsCount += 1
          val tmp = a
          a = b
          b = tmp + a

          for (k <- 0 until 100) {
            lock.enter {
              iterationsCount += 1
              val tmp = a
              a = b
              b = tmp + a
            }
          }
        }
      }
    }

    for (th <- threads) th.join(5.seconds.toMillis)
    assert(iterationsCount === 10100000)
    assert(b === finalResult)
  }

  def startThread(name: String)(cb: => Unit) = {
    val th = new Thread(Runnable(cb))
    th.setName(s"monifu-reentrant-lock-test-$name")
    th.setDaemon(true)
    th.start()
    th
  }
}
