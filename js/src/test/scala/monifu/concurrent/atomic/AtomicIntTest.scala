package monifu.concurrent.atomic

import utest._

object AtomicIntTest extends TestSuite {
  def tests = TestSuite {
    "AtomicInt" - {
      "set" - {
        val r = Atomic(11)
        assert(r.get == 11)

        r.set(100)
        assert(r.get == 100)
      }

      "getAndSet" - {
        val r = Atomic(100)
        assert(r.getAndSet(200) == 100)
        assert(r.getAndSet(0) == 200)
        assert(r.get == 0)
      }

      "compareAndSet" - {
        val r = Atomic(0)

        assert(r.compareAndSet(0, 100))
        assert(r.get == 100)
        assert(!r.compareAndSet(0, 200))
        assert(r.get == 100)
        assert(r.compareAndSet(100, 200))
        assert(r.get == 200)
      }

      "increment" - {
        val r = Atomic(0)
        assert(r.get == 0)

        r.increment
        assert(r.get == 1)
        r.increment
        assert(r.get == 2)
      }

      "incrementNumber" - {
        val r = Atomic(0)
        assert(r.get == 0)

        r.increment(2)
        assert(r.get == 2)

        r.increment(Int.MaxValue - 2)
        assert(r.get == Int.MaxValue)
      }

      "decrement" - {
        val r = Atomic(1)
        assert(r.get == 1)

        r.decrement
        assert(r.get == 0)
        r.decrement
        assert(r.get == -1)
        r.decrement
        assert(r.get == -2)
      }

      "decrementNumber" - {
        val r = Atomic(1)
        assert(r.get == 1)

        r.decrement(3)
        assert(r.get == -2)
      }

      "incrementAndGet" - {
        val r = Atomic(Int.MaxValue - 2)
        assert(r.incrementAndGet == Int.MaxValue - 1)
        assert(r.incrementAndGet == Int.MaxValue)
      }

      "getAndIncrement" - {
        val r = Atomic(126)
        assert(r.getAndIncrement == 126)
        assert(r.getAndIncrement == 127)
        assert(r.getAndIncrement == 128)
        assert(r.get == 129)
      }

      "decrementAndGet" - {
        val r = Atomic(Int.MinValue + 2)
        assert(r.decrementAndGet == Int.MinValue + 1)
        assert(r.decrementAndGet == Int.MinValue)
      }

      "getAndDecrement" - {
        val r = Atomic(Int.MinValue + 2)
        assert(r.getAndDecrement == Int.MinValue + 2)
        assert(r.getAndDecrement == Int.MinValue + 1)
        assert(r.getAndDecrement == Int.MinValue)
      }

      "incrementAndGetNumber" - {
        val r = Atomic(Int.MaxValue - 4)
        assert(r.incrementAndGet(2) == Int.MaxValue - 2)
        assert(r.incrementAndGet(2) == Int.MaxValue)
      }

      "getAndIncrementNumber" - {
        val r = Atomic(Int.MaxValue - 4)
        assert(r.getAndIncrement(2) == Int.MaxValue - 4)
        assert(r.getAndIncrement(2) == Int.MaxValue - 2)
        assert(r.getAndIncrement(2) == Int.MaxValue)
      }

      "decrementAndGetNumber" - {
        val r = Atomic(Int.MaxValue)
        assert(r.decrementAndGet(2) == Int.MaxValue - 2)
        assert(r.decrementAndGet(2) == Int.MaxValue - 4)
        assert(r.decrementAndGet(2) == Int.MaxValue - 6)
      }

      "getAndDecrementNumber" - {
        val r = Atomic(10)
        assert(r.getAndDecrement(2) == 10)
        assert(r.getAndDecrement(2) == 8)
        assert(r.getAndDecrement(2) == 6)
        assert(r.getAndDecrement(2) == 4)
        assert(r.getAndDecrement(2) == 2)        
      }
    }
  }
}