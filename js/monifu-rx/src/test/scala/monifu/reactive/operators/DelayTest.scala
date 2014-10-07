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
import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering}
import monifu.reactive.channels.PublishChannel
import monifu.reactive.subjects.PublishSubject
import monifu.reactive.{Ack, BufferOverflowException, Observable}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.test.JasmineTest


object DelayTest extends JasmineTest {
  implicit val scheduler = Scheduler.trampoline()

  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.delayFirst(timespan)") {
    it("should work") {
      val now = System.currentTimeMillis()
      val f = Observable.repeat(1)
        .take(100000).delayFirst(200.millis).take(5).reduce(_ + _).asFuture

      jasmine.Clock.tick(200)
      expect(f.value.get.get.get).toBe(5)
      val delayed = System.currentTimeMillis() - now
      expect(delayed >= 200).toBe(true)
    }

    it("should stream onError immediately") {
      val f = Observable.error(new RuntimeException("DUMMY"))
        .delayFirst(10.seconds).asFuture
      jasmine.Clock.tick(1)
      expect(f.value.get.failed.get.getMessage).toBe("DUMMY")
    }

    it("should be relative to the first event being emitted") {
      val channel = PublishChannel[Int]()
      val f = channel.delayFirst(200.millis).asFuture

      scheduler.scheduleOnce(200.millis, {
        channel.pushNext(1)
      })

      jasmine.Clock.tick(200)
      expect(f.isCompleted).toBe(false)

      jasmine.Clock.tick(100)
      expect(f.isCompleted).toBe(false)

      jasmine.Clock.tick(100)
      expect(f.isCompleted).toBe(true)
      expect(f.value.get.get.get).toBe(1)
    }

    it("should be relative to onComplete if observable is empty") {
      val channel = PublishChannel[Int]()
      val f = channel.delayFirst(200.millis).asFuture

      scheduler.scheduleOnce(200.millis, {
        channel.pushComplete()
      })

      jasmine.Clock.tick(200)
      expect(f.isCompleted).toBe(false)

      jasmine.Clock.tick(100)
      expect(f.isCompleted).toBe(false)

      jasmine.Clock.tick(100)
      expect(f.isCompleted).toBe(true)
      expect(f.value.get.get.isEmpty).toBe(true)
    }
  }

  describe("Observable.delayFirstOnFuture") {
    it("should delay until the future completes with success") {
      val trigger = Promise[Unit]()
      val obs = Observable.unit(1).delayFirstOnFuture(trigger.future)
      val f = obs.asFuture

      jasmine.Clock.tick(1)
      expect(f.value.isEmpty).toBe(true)

      trigger.success(())
      jasmine.Clock.tick(1)
      expect(f.value.get.get.get).toBe(1)
    }

    it("should interrupt when the future terminates in error") {
      val trigger = Promise[Unit]()
      val obs = Observable.unit(1).delayFirstOnFuture(trigger.future)
      val f = obs.asFuture
      expect(f.isCompleted).toBe(false)

      trigger.failure(new RuntimeException("DUMMY"))
      jasmine.Clock.tick(1)
      expect(f.value.get.failed.get.getMessage).toBe("DUMMY")
    }

    it("should fail with a buffer overflow in case the policy is OverflowTriggering") {
      val trigger = Promise[Unit]()
      val obs = Observable.repeat(1).delayFirstOnFuture(trigger.future, OverflowTriggering(1000))
      val f = obs.asFuture
      jasmine.Clock.tick(1)
      val ex = f.value.get.failed.get
      expect(ex.isInstanceOf[BufferOverflowException]).toBe(true)
    }

    it("should do back-pressure when the policy is BackPressured") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      val f = subject.delayFirstOnFuture(trigger.future, BackPressured(1000))
        .reduce(_ + _).asFuture

      var ack = subject.onNext(1)
      var buffered = 0

      while (ack.isCompleted) {
        expect(ack.value.get.get.toString).toBe("Continue")
        buffered += 1
        ack = subject.onNext(1)
      }

      expect(buffered).toBe(1000)

      trigger.success(())
      ack.onComplete(_ => subject.onComplete())

      jasmine.Clock.tick(1)
      expect(f.value.get.get.get).toBe(1001)
    }

    it("should trigger error immediately when the policy is BackPressured") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      var triggeredError = null : Throwable
      var sum = 0

      subject.delayFirstOnFuture(trigger.future, BackPressured(1000))
        .subscribe(
          elem => { sum += elem; Continue },
          error => { triggeredError = error }
        )

      var ack = Continue : Future[Ack]
      for (_ <- 0 until 1000) {
        ack = subject.onNext(1)
        expect(ack.value.get.get.toString).toBe("Continue")
      }

      subject.onNext(1)
      trigger.failure(new RuntimeException("DUMMY"))
      ack.onComplete(_ => subject.onComplete())
      jasmine.Clock.tick(1)

      expect(sum).toBe(0)
      expect(triggeredError.getMessage).toBe("DUMMY")
    }

    it("should trigger error immediately when the policy is OverflowTriggering") {
      val trigger = Promise[Unit]()
      val subject = PublishSubject[Int]()
      var triggeredError = null : Throwable
      var sum = 0

      subject.delayFirstOnFuture(trigger.future, OverflowTriggering(1000))
        .subscribe(
          elem => { sum += elem; Continue },
          error => { triggeredError = error }
        )

      var ack = Continue : Future[Ack]
      for (_ <- 0 until 1000) {
        ack = subject.onNext(1)
        expect(ack.value.get.get.toString).toBe("Continue")
      }

      trigger.failure(new RuntimeException("DUMMY"))
      ack.onComplete(_ => subject.onComplete())
      jasmine.Clock.tick(1)

      expect(sum).toBe(0)
      expect(triggeredError.getMessage).toBe("DUMMY")    }
  }
}
