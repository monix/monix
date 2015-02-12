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
import monifu.reactive.Observable
import org.scalatest.FunSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, TimeoutException}


class DelaySubscriptionTest extends FunSpec {
  describe("Observable.delaySubscription(timespan)") {
    it("should work") {
      val now = System.currentTimeMillis()
      val f = Observable.repeat(1).take(100000)
        .delaySubscription(200.millis)
        .reduce(_ + _).asFuture

      val r = Await.result(f, 5.seconds)
      assert(r === Some(100000))
      val delayed = System.currentTimeMillis() - now
      assert(delayed >= 200, s"$delayed millis > 200 millis")
    }
  }

  describe("Observable.delaySubscription(future)") {
    it("should work") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      intercept[TimeoutException] {
        Await.result(f, 200.millis)
      }

      trigger.success(())
      val r = Await.result(f, 5.seconds)
      assert(r === Some(100000))
    }

    it("should trigger error") {
      val trigger = Promise[Unit]()

      val f = Observable.repeat(1).take(100000)
        .delaySubscription(trigger.future)
        .reduce(_ + _).asFuture

      intercept[TimeoutException] {
        Await.result(f, 200.millis)
      }

      class DummyException extends RuntimeException
      trigger.failure(new DummyException)
      intercept[DummyException] {
        Await.result(f, 5.seconds)
      }
    }
  }
}
