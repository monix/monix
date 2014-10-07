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

package monifu.reactive.streams

import java.util.concurrent.{CountDownLatch, TimeUnit}
import monifu.reactive.Observable
import monifu.reactive.internals.FutureAckExtensions
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.FunSpec
import monifu.concurrent.Implicits.globalScheduler


class ObservableIsPublisherTest extends FunSpec {
  describe("Observable.subscribe(subscriber)") {
    it("should work with stop-and-wait back-pressure") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).subscribe(new Subscriber[Int] {
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
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should work with batched execution") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).subscribe(new Subscriber[Int] {
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
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should merge in batches of 1000, test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(new Subscriber[Int] {
          private[this] var s = null : Subscription
          private[this] var requested = 1000

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(requested)
          }

          def onNext(elem: Int): Unit = {
            sum += elem

            requested -= 1
            if (requested == 0) {
              s.request(1000)
              requested = 1000
            }
          }

          def onError(ex: Throwable): Unit = {
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should merge in batches of 1000, test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(new Subscriber[Int] {
          private[this] var s = null : Subscription
          private[this] var requested = 1000

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(requested)
          }

          def onNext(elem: Int): Unit = {
            requested -= 1
            if (requested == 0) {
              s.request(1000)
              requested = 1000
            }

            sum += elem
          }

          def onError(ex: Throwable): Unit = {
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should merge in batches of 1, test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(new Subscriber[Int] {
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
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should merge in batches of 1, test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x))
          .subscribe(new Subscriber[Int] {
          private[this] var s = null : Subscription

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onNext(elem: Int): Unit = {
            s.request(1)
            sum += elem
          }

          def onError(ex: Throwable): Unit = {
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should merge.take(1), test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x)).take(1)
          .subscribe(new Subscriber[Int] {
            def onSubscribe(s: Subscription): Unit = {
              s.request(1000)
            }

            def onNext(elem: Int): Unit = {
              sum += elem
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 1)
      }
    }

    it("should merge.take(1), test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var seen = 0

        Observable.from(1 to 10000).mergeMap(x => Observable.unit(x)).take(1)
          .subscribe(new Subscriber[Int] {
            private[this] var sub: Subscription = null
            def onSubscribe(s: Subscription): Unit = {
              sub = s
              s.request(1)
            }

            def onNext(elem: Int): Unit = {
              assert(seen === 0)
              seen = elem
              sub.cancel()
              completed.countDown()
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              throw new IllegalStateException("onComplete()")
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should flatMap in batches of 1000, test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).subscribe(
          new Subscriber[Int] {
            private[this] var s = null : Subscription
            private[this] var requested = 1000

            def onSubscribe(s: Subscription): Unit = {
              this.s = s
              s.request(requested)
            }

            def onNext(elem: Int): Unit = {
              sum += elem

              requested -= 1
              if (requested == 0) {
                s.request(1000)
                requested = 1000
              }
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should flatMap in batches of 1000, test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).subscribe(
          new Subscriber[Int] {
            private[this] var s = null : Subscription
            private[this] var requested = 1000

            def onSubscribe(s: Subscription): Unit = {
              this.s = s
              s.request(requested)
            }

            def onNext(elem: Int): Unit = {
              requested -= 1
              if (requested == 0) {
                s.request(1000)
                requested = 1000
              }

              sum += elem
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should flatMap in batches of 1, test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).subscribe(
          new Subscriber[Int] {
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
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should flatMap in batches of 1, test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).subscribe(
          new Subscriber[Int] {
            private[this] var s = null : Subscription

            def onSubscribe(s: Subscription): Unit = {
              this.s = s
              s.request(1)
            }

            def onNext(elem: Int): Unit = {
              s.request(1)
              sum += elem
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 5000 * 10001)
      }
    }

    it("should flatMap.take(1), test 1") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).take(1)
          .subscribe(new Subscriber[Int] {
            def onSubscribe(s: Subscription): Unit = {
              s.request(1000)
            }

            def onNext(elem: Int): Unit = {
              sum += elem
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              completed.countDown()
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 1)
      }
    }

    it("should flatMap.take(1), test 2") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var seen = 0

        Observable.from(1 to 10000).flatMap(x => Observable.unit(x)).take(1)
          .subscribe(new Subscriber[Int] {
          private[this] var sub: Subscription = null
            def onSubscribe(s: Subscription): Unit = {
              sub = s
              s.request(1)
            }

            def onNext(elem: Int): Unit = {
              assert(seen === 0)
              seen = elem
              sub.cancel()
              completed.countDown()
            }

            def onError(ex: Throwable): Unit = {
              globalScheduler.reportFailure(ex)
            }

            def onComplete(): Unit = {
              throw new IllegalStateException("onComplete()")
            }
          })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should work for Observable.unit, test 1") {
      for (_ <- 0 until 100) {
        var seen = 0
        val completed = new CountDownLatch(1)

        Observable.unit(1).subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onError(ex: Throwable): Unit =
            globalScheduler.reportFailure(ex)

          def onComplete(): Unit =
            completed.countDown()

          def onNext(elem: Int): Unit = {
            seen = elem
            s.request(1)
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should work for Observable.unit, test 2") {
      for (_ <- 0 until 100) {
        var seen = 0
        val completed = new CountDownLatch(1)

        Observable.unit(1).subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            globalScheduler.execute(new Runnable {
              def run(): Unit = s.request(1)
            })
          }

          def onError(ex: Throwable): Unit =
            globalScheduler.reportFailure(ex)

          def onComplete(): Unit =
            completed.countDown()

          def onNext(elem: Int): Unit = {
            seen = elem
            s.request(1)
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should work for Observable.unit, test 3") {
      for (_ <- 0 until 100) {
        var seen = 0
        val completed = new CountDownLatch(1)

        Observable.unit(1).subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            globalScheduler.execute(new Runnable {
              def run(): Unit = s.request(1)
            })
          }

          def onError(ex: Throwable): Unit =
            globalScheduler.reportFailure(ex)

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete()")
          }

          def onNext(elem: Int): Unit = {
            seen = elem
            s.cancel()
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should cancel with stop-and-wait back-pressure") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).asyncBoundary().subscribe(new Subscriber[Int] {
          private[this] var s = null : Subscription
          private[this] var processed = 0

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onNext(elem: Int): Unit = {
            sum += elem
            processed += 1
            if (processed < 5000)
              s.request(1)
            else if (processed == 5000) {
              s.cancel()
              completed.countDown()
            }
            else
              throw new IllegalStateException(s"onNext($elem)")
          }

          def onError(ex: Throwable): Unit = {
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            throw new IllegalStateException(s"onComplete()")
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 2500 * 5001)
      }
    }

    it("should cancel with batched requests") {
      for (_ <- 0 until 100) {
        val completed = new CountDownLatch(1)
        var sum = 0

        Observable.from(1 to 10000).asyncBoundary().subscribe(new Subscriber[Int] {
          private[this] var s = null : Subscription
          private[this] var processed = 0
          private[this] var requested = 100

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(requested)
          }

          def onNext(elem: Int): Unit = {
            sum += elem
            processed += 1
            if (processed < 5000) {
              requested -= 1
              if (requested == 0) {
                s.request(100)
                requested = 100
              }
            }
            else if (processed == 5000) {
              s.cancel()
              completed.countDown()
            }
            else
              throw new IllegalStateException(s"onNext($elem)")
          }

          def onError(ex: Throwable): Unit = {
            globalScheduler.reportFailure(ex)
          }

          def onComplete(): Unit = {
            throw new IllegalStateException(s"onComplete()")
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 2500 * 5001)
      }
    }

    it("should receive 1 element and cancel") {
      for (_ <- 0 until 100000) {
        val completed = new CountDownLatch(1)

        val obs = Observable.create[Int] { observer =>
          observer.onNext(1).onContinue {
            observer.onNext(2)
            observer.onComplete()
          }
        }

        var seen = 0
        obs.subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(ex)
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete()")
          }

          def onNext(elem: Int): Unit = {
            seen = elem
            s.cancel()
            completed.countDown()
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(seen === 1)
      }
    }

    it("should receive 2 elements and cancel, test 1") {
      for (_ <- 0 until 100000) {
        val completed = new CountDownLatch(1)

        val obs = Observable.create[Int] { observer =>
          observer.onNext(1).onContinue {
            observer.onNext(2)
            observer.onComplete()
          }
        }

        var sum = 0
        obs.subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          private[this] var received = 0

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(ex)
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete()")
          }

          def onNext(elem: Int): Unit = {
            sum += elem
            received += 1
            if (received == 2) {
              s.cancel()
              completed.countDown()
            }
            else
              s.request(1)
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 3)
      }
    }

    it("should receive 2 elements and cancel, test 2") {
      for (_ <- 0 until 100000) {
        val completed = new CountDownLatch(1)

        val obs = Observable.create[Int] { observer =>
          observer.onNext(1).onContinue {
            observer.onNext(2)
            observer.onComplete()
          }
        }

        var sum = 0
        obs.subscribe(new Subscriber[Int] {
          private[this] var s: Subscription = null
          private[this] var received = 0

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            s.request(1)
          }

          def onError(ex: Throwable): Unit = {
            throw new IllegalStateException(ex)
          }

          def onComplete(): Unit = {
            throw new IllegalStateException("onComplete()")
          }

          def onNext(elem: Int): Unit = {
            received += 1
            if (received == 2) {
              s.cancel()
              sum += elem
              completed.countDown()
            }
            else {
              s.request(1)
              sum += elem
            }
          }
        })

        assert(completed.await(5, TimeUnit.SECONDS), "completed.await should have succeeded")
        assert(sum === 3)
      }
    }
  }
}
