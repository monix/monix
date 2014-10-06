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

import java.util.concurrent.{CountDownLatch, TimeUnit}

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable
import monifu.reactive.channels.PublishChannel
import monifu.reactive.subjects.PublishSubject
import org.scalatest.FunSpec

import scala.concurrent.Await
import scala.concurrent.duration._


class TakeUntilTest extends FunSpec {
  describe("Observable.takeUntil(other: Observable)") {
    it("should emit everything in case the other observable does not emit anything") {
      val other = Observable.never
      val f = Observable.from(0 until 1000)
        .delayFirst(200.millis) // introducing artificial delay
        .takeUntilOtherEmits(other)
        .reduce(_ + _)
        .asFuture

      val r = Await.result(f, 5.seconds)
      assert(r === Some(500 * 999))
    }

    it("should stop in case the other observable signals onNext") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex,
        () => completed.countDown()
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushNext(())
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }

    it("should stop in case the other observable signals onComplete") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val completed = new CountDownLatch(1)
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => throw ex,
        () => completed.countDown()
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushComplete()
      assert(completed.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }

    it("should stop with error in case the other observable signals onError") {
      val trigger = PublishChannel[Unit]()
      val channel = PublishSubject[Int]()
      val errorThrown = new CountDownLatch(1)
      var sum = 0

      channel.takeUntilOtherEmits(trigger).subscribe(
        elem => { sum += elem; Continue },
        ex => { errorThrown.countDown() }
      )

      for (_ <- 0 until 1000) {
        val ack = channel.onNext(1)
        assert(ack.value.get === Continue.IsSuccess)
      }

      trigger.pushError(new RuntimeException("DUMMY"))
      assert(errorThrown.await(5, TimeUnit.SECONDS), "completed.await")
      assert(sum === 1000)
    }
  }
}
