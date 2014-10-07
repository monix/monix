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

package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Observable

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.scalajs.test.JasmineTest


object DelaySubscriptionTest extends JasmineTest {
  implicit val s = Scheduler.trampoline()

  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.delaySubscription(timespan)") {
    it("should work") {
      val f = Observable.repeat(1).take(100000)
        .delaySubscription(200.millis)
        .reduce(_ + _).asFuture

      jasmine.Clock.tick(199)
      expect(f.isCompleted).toBe(false)
      jasmine.Clock.tick(1)
      expect(f.isCompleted).toBe(true)
    }
  }

  describe("Observable.delaySubscription(future)") {
    it("should work") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      jasmine.Clock.tick(100)
      expect(f.isCompleted).toBe(false)

      trigger.success(())
      jasmine.Clock.tick(1)
      expect(f.isCompleted).toBe(true)
      expect(f.value.get.get.get).toBe(100000)
    }

    it("should trigger error") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      trigger.failure(new RuntimeException("DUMMY"))
      jasmine.Clock.tick(1)
      expect(f.isCompleted).toBe(true)
      expect(f.value.get.failed.get.getMessage).toBe("DUMMY")
    }
  }
}
