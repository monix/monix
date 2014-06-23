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

import java.util.concurrent.{TimeUnit, CountDownLatch}

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global

class ReactiveStreamsSanityTest extends FunSpec {
  describe("Observable.subscribe(subscriber)") {
    it("should work with stop-and-wait back-pressure") {
      val completed = new CountDownLatch(1)
      var sum = 0

      Observable.from(0 until 10000).subscribe(new Subscriber[Int] {
        private[this] var s = null : Subscription
        def onSubscribe(s: Subscription): Unit = {
          this.s = s
          s.request(1)
        }

        def onNext(elem: Int): Unit = {
          sum += elem
          s.request(1)
        }

        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 10000).sum)
    }

    it("should work with batched execution") {
      val completed = new CountDownLatch(1)
      var sum = 0

      Observable.from(0 until 10000).subscribe(new Subscriber[Int] {
        private[this] var s = null : Subscription
        private[this] var leftToProcess = 1000

        def onSubscribe(s: Subscription): Unit = {
          this.s = s
          s.request(leftToProcess)
        }

        def onNext(elem: Int): Unit = {
          sum += elem
          leftToProcess -= 1
          if (leftToProcess == 0) {
            leftToProcess = 1000
            s.request(1000)
          }
        }

        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 10000).sum)
    }

    it("should work with infinite requests") {
      val completed = new CountDownLatch(1)
      var sum = 0

      Observable.from(0 until 10000).subscribe(new Subscriber[Int] {
        private[this] var s = null : Subscription
        def onSubscribe(s: Subscription): Unit = {
          this.s = s
          s.request(-1)
        }

        def onNext(elem: Int): Unit = {
          sum += elem
        }

        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === (0 until 10000).sum)
    }
  }
}
