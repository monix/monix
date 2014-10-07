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

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable
import monifu.reactive.channels.PublishChannel
import monifu.reactive.subjects.PublishSubject

import scala.concurrent.duration._
import scala.scalajs.test.JasmineTest


object TakeUntilTest extends JasmineTest {
  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.takeUntil(other: Observable)") {
    it("should emit everything in case the other observable does not emit anything") {
      val other = Observable.never
      val f = Observable.from(0 until 1000)
        .delayFirst(200.millis) // introducing artificial delay
        .takeUntilOtherEmits(other)
        .reduce(_ + _)
        .asFuture

      jasmine.Clock.tick(199)
      expect(f.isCompleted).toBe(false)
      jasmine.Clock.tick(1)
      expect(f.isCompleted).toBe(true)
      expect(f.value.get.get.get).toBe(500 * 999)
    }

    it("should stop in case the other observable signals onNext") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        expect(ack.value.get.get.toString).toBe("Continue")
      }

      trigger.pushNext(())
      jasmine.Clock.tick(1)
      expect(sum).toBe(1000)
    }

    it("should stop in case the other observable signals onComplete") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        expect(ack.value.get.get.toString).toBe("Continue")
      }

      trigger.pushComplete()
      jasmine.Clock.tick(1)
      expect(sum).toBe(1000)
    }

    it("should stop with error in case the other observable signals onError") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      var errorThrown = null : Throwable
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => { errorThrown = ex }
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        expect(ack.value.get.get.toString).toBe("Continue")
      }

      trigger.pushError(new RuntimeException("DUMMY"))
      jasmine.Clock.tick(1)
      expect(errorThrown.getMessage).toBe("DUMMY")
      expect(sum).toBe(1000)
    }
  }
}
