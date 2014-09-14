/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive

import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.scalatest.FunSpec
import scala.concurrent.ExecutionContext.Implicits.global


class MulticastTest extends FunSpec {
  describe("Observable.multicast") {
    it("should not subscribe observers until connect() happens") {
      val latch = new CountDownLatch(2)
      val obs = Observable.from(1 until 100).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")

      obs.connect()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should complete observers after cancel()") {
      val latch = new CountDownLatch(2)
      val obs = Observable.repeat(()).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()
      val sub = obs.connect()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")

      sub.cancel()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should not connect() again, once canceled") {
      val latch = new CountDownLatch(2)
      val obs = Observable.repeat(()).publish()

      obs.doOnComplete(latch.countDown()).subscribe()
      obs.doOnComplete(latch.countDown()).subscribe()
      val sub = obs.connect()

      assert(!latch.await(100, TimeUnit.MILLISECONDS), "latch.await should have failed")
      sub.cancel()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")

      assert(obs.connect().isCanceled, "Subscription should be canceled, but isn't")

      val onCompleted = new CountDownLatch(1)
      obs.doOnComplete(onCompleted.countDown()).subscribe()
      assert(onCompleted.await(10, TimeUnit.SECONDS), "onComplete.await should have succeeded")
    }
  }
}
