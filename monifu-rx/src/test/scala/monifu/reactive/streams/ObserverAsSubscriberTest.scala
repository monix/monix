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

package monifu.reactive.streams

import java.util.concurrent.{CountDownLatch, TimeUnit}

import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observable, Observer}
import org.scalatest.FunSpec

import scala.concurrent.Future


class ObserverAsSubscriberTest extends FunSpec {

  describe("ObserverAsSubscriber") {
    it("should work") {
      var sum = 0
      val completed = new CountDownLatch(1)

      val subscriber = ObserverAsSubscriber(new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      Observable.range(1, 100001).subscribe(subscriber)

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 50000 * 100001)
    }

    it("should work with asynchronous boundaries and batched requests") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) =
            Future {
              sum += elem
              Continue
            }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 128))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should work with asynchronous boundaries and requests of size 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) =
            Future {
              sum += elem
              Continue
            }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should work synchronously and with batched requests") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) ={
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 128))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should work synchronously and with requests of size 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) = {
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should do flatMap with batched requests") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) = {
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x))
          .subscribe(Observer.asSubscriber(observer, requestSize = 128))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should do flatMap with requests of size 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) = {
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x))
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should do mergeMap with batched requests") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) = {
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(Observer.asSubscriber(observer, requestSize = 128))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should do mergeMap with requests of size 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          def onNext(elem: Int) = {
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        }

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should cancel precisely, test 1") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          private[this] var received = 0

          def onNext(elem: Int) = Future {
            received += 1
            sum += elem
            if (received < 10) Continue
            else if (received == 10) {
              completed.countDown()
              Cancel
            }
            else
              throw new IllegalStateException(s"onNext($elem)")
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete")
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5 * 11)
      }
    }

    it("should cancel precisely, test 2") {
      for (_ <- 0 until 10000) {
        val completed = new CountDownLatch(1)
        var sum = 0

        val observer = new Observer[Int] {
          private[this] var received = 0

          def onNext(elem: Int) = {
            received += 1
            sum += elem
            if (received < 100)
              Continue
            else if (received == 100) {
              completed.countDown()
              Cancel
            }
            else
              throw new IllegalStateException(s"onNext($elem)")
          }

          def onError(ex: Throwable): Unit = {
            throw ex
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete")
          }
        }

        Observable.from(1 to 10000)
          .subscribe(Observer.asSubscriber(observer, requestSize = 1))

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 50 * 101)
      }
    }
  }
}
