/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent.atomic

import scala.scalajs.test.JasmineTest

object AtomicIntTest extends JasmineTest {
  describe("AtomicInt") {
    it("should set") {
      val r = Atomic(11)
      expect(r.get).toBe(11)

      r.set(100)
      expect(r.get).toBe(100)
    }

    it("should getAndSet") {
      val r = Atomic(100)
      expect(r.getAndSet(200)).toBe(100)
      expect(r.getAndSet(0)).toBe(200)
      expect(r.get).toBe(0)
    }

    it("should compareAndSet") {
      val r = Atomic(0)

      expect(r.compareAndSet(0, 100)).toBe(true)
      expect(r.get).toBe(100)
      expect(r.compareAndSet(0, 200)).toBe(false)
      expect(r.get).toBe(100)
      expect(r.compareAndSet(100, 200)).toBe(true)
      expect(r.get).toBe(200)
    }

    it("should increment") {
      val r = Atomic(0)
      expect(r.get).toBe(0)

      r.increment()
      expect(r.get).toBe(1)
      r.increment()
      expect(r.get).toBe(2)
    }

    it("should incrementNumber") {
      val r = Atomic(0)
      expect(r.get).toBe(0)

      r.increment(2)
      expect(r.get).toBe(2)

      r.increment(Int.MaxValue - 2)
      expect(r.get).toBe(Int.MaxValue)
    }

    it("should decrement") {
      val r = Atomic(1)
      expect(r.get).toBe(1)

      r.decrement()
      expect(r.get).toBe(0)
      r.decrement()
      expect(r.get).toBe(-1)
      r.decrement()
      expect(r.get).toBe(-2)
    }

    it("should decrementNumber") {
      val r = Atomic(1)
      expect(r.get).toBe(1)

      r.decrement(3)
      expect(r.get).toBe(-2)
    }

    it("should incrementAndGet") {
      val r = Atomic(Int.MaxValue - 2)
      expect(r.incrementAndGet()).toBe(Int.MaxValue - 1)
      expect(r.incrementAndGet()).toBe(Int.MaxValue)
    }

    it("should getAndIncrement") {
      val r = Atomic(126)
      expect(r.getAndIncrement()).toBe(126)
      expect(r.getAndIncrement()).toBe(127)
      expect(r.getAndIncrement()).toBe(128)
      expect(r.get).toBe(129)
    }

    it("should decrementAndGet") {
      val r = Atomic(Int.MinValue + 2)
      expect(r.decrementAndGet()).toBe(Int.MinValue + 1)
      expect(r.decrementAndGet()).toBe(Int.MinValue)
    }

    it("should getAndDecrement") {
      val r = Atomic(Int.MinValue + 2)
      expect(r.getAndDecrement()).toBe(Int.MinValue + 2)
      expect(r.getAndDecrement()).toBe(Int.MinValue + 1)
      expect(r.getAndDecrement()).toBe(Int.MinValue)
    }

    it("should incrementAndGetNumber") {
      val r = Atomic(Int.MaxValue - 4)
      expect(r.incrementAndGet(2)).toBe(Int.MaxValue - 2)
      expect(r.incrementAndGet(2)).toBe(Int.MaxValue)
    }

    it("should getAndIncrementNumber") {
      val r = Atomic(Int.MaxValue - 4)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue - 4)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue - 2)
      expect(r.getAndIncrement(2)).toBe(Int.MaxValue)
    }

    it("should decrementAndGetNumber") {
      val r = Atomic(Int.MaxValue)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 2)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 4)
      expect(r.decrementAndGet(2)).toBe(Int.MaxValue - 6)
    }

    it("should getAndDecrementNumber") {
      val r = Atomic(10)
      expect(r.getAndDecrement(2)).toBe(10)
      expect(r.getAndDecrement(2)).toBe(8)
      expect(r.getAndDecrement(2)).toBe(6)
      expect(r.getAndDecrement(2)).toBe(4)
      expect(r.getAndDecrement(2)).toBe(2)
    }
  }

}
