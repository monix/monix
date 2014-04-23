package monifu.concurrent.schedulers

import scala.scalajs.test.JasmineTest
import scala.concurrent.Promise
import concurrent.duration._
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.atomic.Atomic

object AsyncSchedulerTest extends JasmineTest {
  implicit val s = AsyncScheduler

  describe("AsyncScheduler") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should scheduleOnce") {
      val p = Promise[Int]()
      s.scheduleOnce { p.success(1) }
      val f = p.future

      jasmine.Clock.tick(1)
      expect(f.value.get.get).toBe(1)
    }

    it("should scheduleOnce with delay") {
      val p = Promise[Int]()
      s.scheduleOnce(100.millis, p.success(1))
      val f = p.future

      jasmine.Clock.tick(99)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
      jasmine.Clock.tick(1)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(1)
    }

    it("should scheduleOnce with delay and cancel") {
      val p = Promise[Int]()
      val f = p.future
      val task = s.scheduleOnce(100.millis, p.success(1))
      task.cancel()

      jasmine.Clock.tick(100)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
    }

    it("should schedule") {
      val p = Promise[Int]()
      val f = p.future
      s.schedule(s2 => s2.scheduleOnce(p.success(1)))

      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
      jasmine.Clock.tick(1)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(1)
    }

    it("should schedule with delay") {
      val p = Promise[Int]()
      val f = p.future
      s.schedule(100.millis, s2 => s2.scheduleOnce(100.millis, p.success(1)))

      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
      jasmine.Clock.tick(100)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
      jasmine.Clock.tick(100)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(1)
    }

    it("should schedule with delay and cancel") {
      val p = Promise[Int]()
      val f = p.future
      val task = s.schedule(100.millis, s2 => s2.scheduleOnce(100.millis, p.success(1)))

      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
      jasmine.Clock.tick(100)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)

      task.cancel()

      jasmine.Clock.tick(100)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(0)
    }

    it("should schedule periodically") {
      val sub = SingleAssignmentCancelable()
      val p = Promise[Int]()
      val value = Atomic(0)
      val f = p.future

      sub() = s.scheduleRepeated(10.millis, 50.millis, {
        if (value.incrementAndGet() > 3) {
          sub.cancel()
          p.success(value.get)
        }
      })

      jasmine.Clock.tick(1000)
      expect(f.value.flatMap(_.toOption).getOrElse(0)).toBe(4)
    }

    it("should execute async") {
      var stackDepth = 0
      var iterations = 0

      s.scheduleOnce { 
      stackDepth += 1
        iterations += 1
        s.scheduleOnce { 
        stackDepth += 1
          iterations += 1
          expect(stackDepth).toBe(1)

          s.scheduleOnce { 
          stackDepth += 1
            iterations += 1
            expect(stackDepth).toBe(1)
            stackDepth -= 1
          }

          expect(stackDepth).toBe(1)
          stackDepth -= 1
        }
        expect(stackDepth).toBe(1)
        stackDepth -= 1
      }

      jasmine.Clock.tick(1)
      expect(iterations).toBe(3)
      expect(stackDepth).toBe(0)
    }
  }
}
