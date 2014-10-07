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

package monifu.concurrent.schedulers

import monifu.concurrent.UncaughtExceptionReporter

import scala.scalajs.test.JasmineTest
import scala.concurrent.{Future, Promise}
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.atomic.Atomic
import concurrent.duration._
import scala.util.Try


object TrampolineSchedulerTest extends JasmineTest {
  describe("TrampolineScheduler") {
    val s = TrampolineScheduler(UncaughtExceptionReporter { ex =>
      if (!ex.getMessage.contains("test-exception"))
        throw ex
    })

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

    it("should not trigger a stack overflow") {
      var effect = 0
      def loop(until: Int): Unit =
        if (effect < until)
          s.scheduleOnce({
            effect += 1
            loop(until)
          })

      loop(until = 100000)
      jasmine.Clock.tick(1)

      expect(effect).toBe(100000)
    }

    it("should immediately execute when no scheduling or errors") {
      implicit val ec = s
      val seed = 100
      var effect = 0

      for (v1 <- Future(seed).map(_ * 100).filter(_ % 2 == 0); v2 <- Future(seed * 3).map(_ - 100)) {
        effect = v1 + v2
      }

      expect(effect).toBe(10000 + 200)
    }

    it("triggering an exception should continue pending tasks") {
      val p = Promise[String]()

      s.scheduleOnce({
        s.scheduleOnce({
          p.success("result")
        })

        throw new RuntimeException("test-exception-please-ignore")
      })

      expect(p.future.value.getOrElse(Try("unfinished-result")).get)
        .toBe("result")
    }
  }
}