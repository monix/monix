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

import monifu.concurrent.Scheduler
import scala.scalajs.test.JasmineTest
import scala.concurrent.Promise
import concurrent.duration._
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.atomic.Atomic

object AsyncSchedulerTest extends JasmineTest {
  implicit val s = Scheduler()

  describe("AsyncScheduler") {
    beforeEach {
      jasmine.Clock.useMock()
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
  }
}
