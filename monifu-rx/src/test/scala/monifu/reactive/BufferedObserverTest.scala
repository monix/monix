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

import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering, Unbounded}
import monifu.reactive.observers.BufferedObserver
import org.scalatest.FunSpec

import monifu.concurrent.Implicits.globalScheduler
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}


class BufferedObserverTest extends FunSpec {
  describe("BufferedObserver(OverflowTriggering)") {
    it("should not lose events, test 1") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, OverflowTriggering(100000))
      for (i <- 0 until 100000) buffer.onNext(i)
      buffer.onComplete()

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 100000)
    }

    it("should not lose events, test 2") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, OverflowTriggering(100000))

      def loop(n: Int): Unit =
        if (n > 0) globalScheduler.execute(new Runnable {
          def run() = { buffer.onNext(n); loop(n-1) }
        })
        else buffer.onComplete()

      loop(10000)
      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 10000)
    }

    it("should trigger overflow when over capacity") {
      val errorCaught = new CountDownLatch(1)
      val receivedLatch = new CountDownLatch(5)
      val promise = Promise[Ack]()

      val underlying = new Observer[Int] {
        var received = 0
        def onNext(elem: Int) = {
          received += 1
          if (received < 6) {
            receivedLatch.countDown()
            Continue
          }
          else if (received == 6) {
            receivedLatch.countDown()
            // never ending piece of processing
            promise.future
          }
          else
            Continue
        }

        def onError(ex: Throwable) = {
          assert(ex.isInstanceOf[BufferOverflowException],
            s"Exception $ex is not a buffer overflow error")
          errorCaught.countDown()
        }

        def onComplete() = {
          throw new IllegalStateException("Should not onComplete")
        }
      }

      val buffer = BufferedObserver(underlying, OverflowTriggering(5))

      assert(buffer.onNext(1) === Continue)
      assert(buffer.onNext(2) === Continue)
      assert(buffer.onNext(3) === Continue)
      assert(buffer.onNext(4) === Continue)
      assert(buffer.onNext(5) === Continue)

      assert(receivedLatch.await(10, TimeUnit.SECONDS), "receivedLatch.await should have succeeded")
      assert(!errorCaught.await(2, TimeUnit.SECONDS), "errorCaught.await should have failed")

      buffer.onNext(6)
      for (i <- 0 until 10) buffer.onNext(7)

      promise.success(Continue)
      assert(errorCaught.await(5, TimeUnit.SECONDS), "errorCaught.await should have succeeded")
    }

    it("should send onError when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
      }, OverflowTriggering(5))

      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onError when in flight") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
      }, OverflowTriggering(5))

      buffer.onNext(1)
      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onError when at capacity") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = promise.future
        def onComplete() = throw new IllegalStateException()
      }, OverflowTriggering(5))

      buffer.onNext(1)
      buffer.onNext(2)
      buffer.onNext(3)
      buffer.onNext(4)
      buffer.onNext(5)
      buffer.onError(new RuntimeException("dummy"))

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = latch.countDown()
      }, OverflowTriggering(5))

      buffer.onComplete()
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when in flight") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      }, OverflowTriggering(5))

      buffer.onNext(1)
      buffer.onComplete()
      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when at capacity") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      }, OverflowTriggering(5))

      buffer.onNext(1)
      buffer.onNext(2)
      buffer.onNext(3)
      buffer.onNext(4)
      buffer.onComplete()

      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should do onComplete only after all the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      }, OverflowTriggering(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onComplete only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      }, OverflowTriggering(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onError only after the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      }, OverflowTriggering(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }

    it("should do onError only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      }, OverflowTriggering(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }
  }

  describe("BufferedObserver(Unbounded)") {
    it("should not lose events, test 1") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, Unbounded)
      for (i <- 0 until 100000) buffer.onNext(i)
      buffer.onComplete()

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 100000)
    }

    it("should not lose events, test 2") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, Unbounded)

      def loop(n: Int): Unit =
        if (n > 0) globalScheduler.execute(new Runnable {
          def run() = { buffer.onNext(n); loop(n-1) }
        })
        else buffer.onComplete()

      loop(10000)
      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 10000)
    }

    it("should send onError when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
      })

      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onError when in flight") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
      })

      buffer.onNext(1)
      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = latch.countDown()
      })

      buffer.onComplete()
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when in flight") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      })

      buffer.onNext(1)
      buffer.onComplete()
      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when at capacity") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      })

      buffer.onNext(1)
      buffer.onNext(2)
      buffer.onNext(3)
      buffer.onNext(4)
      buffer.onComplete()

      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should do onComplete only after all the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      })

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onComplete only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      })

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onError only after the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      })

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }

    it("should do onError only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      })

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }
  }

  describe("BufferedObserver(BackPressured)") {
    it("should do back-pressure") {
      val promise = Promise[Ack]()
      val completed = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Int] {
        def onNext(elem: Int) = promise.future
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onComplete() = completed.countDown()
      }, BackPressured(5))

      assert(buffer.onNext(1) === Continue)
      assert(buffer.onNext(2) === Continue)
      assert(buffer.onNext(3) === Continue)
      assert(buffer.onNext(4) === Continue)
      assert(buffer.onNext(5) === Continue)

      val async = buffer.onNext(6)
      assert(async !== Continue)

      promise.success(Continue)
      Await.result(async, 10.seconds)

      assert(buffer.onNext(1) === Continue)
      assert(buffer.onNext(2) === Continue)
      assert(buffer.onNext(3) === Continue)
      assert(buffer.onNext(4) === Continue)
      assert(buffer.onNext(5) === Continue)
      assert(!completed.await(100, TimeUnit.MILLISECONDS), "completed.await shouldn't have succeeded")

      buffer.onComplete()
      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
    }

    it("should not lose events, test 1") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, BackPressured(100000))
      for (i <- 0 until 100000) buffer.onNext(i)
      buffer.onComplete()

      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 100000)
    }

    it("should not lose events, test 2") {
      var number = 0
      val completed = new CountDownLatch(1)

      val underlying = new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          number += 1
          Continue
        }

        def onError(ex: Throwable): Unit = {
          globalScheduler.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      }

      val buffer = BufferedObserver(underlying, BackPressured(100000))

      def loop(n: Int): Unit =
        if (n > 0) globalScheduler.execute(new Runnable {
          def run() = { buffer.onNext(n); loop(n-1) }
        })
        else buffer.onComplete()

      loop(10000)
      assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(number === 10000)
    }

    it("should send onError when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = throw new IllegalStateException()
      }, BackPressured(10000))

      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onError when in flight") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage === "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = Continue
        def onComplete() = throw new IllegalStateException()
      }, BackPressured(1000))

      for (_ <- 0 until 900) buffer.onNext(1)
      buffer.onError(new RuntimeException("dummy"))
      assert(latch.await(20, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when empty") {
      val latch = new CountDownLatch(1)
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = throw new IllegalStateException()
        def onComplete() = latch.countDown()
      }, BackPressured(10000))

      buffer.onComplete()
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when in flight") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      }, BackPressured(10000))

      buffer.onNext(1)
      buffer.onComplete()
      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should send onComplete when at capacity") {
      val latch = new CountDownLatch(1)
      val promise = Promise[Ack]()
      val buffer = BufferedObserver(new Observer[Int] {
        def onError(ex: Throwable) = throw new IllegalStateException()
        def onNext(elem: Int) = promise.future
        def onComplete() = latch.countDown()
      }, BackPressured(10000))

      buffer.onNext(1)
      buffer.onNext(2)
      buffer.onNext(3)
      buffer.onNext(4)
      buffer.onComplete()

      assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

      promise.success(Continue)
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should do onComplete only after all the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      }, BackPressured(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onComplete only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = throw ex
        def onComplete() = complete.countDown()
      }, BackPressured(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onComplete()

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
      assert(sum === (0 until 9999).sum)
    }

    it("should do onError only after the queue was drained") {
      var sum = 0L
      val complete = new CountDownLatch(1)
      val startConsuming = Promise[Continue]()

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          startConsuming.future
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      }, BackPressured(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)
      startConsuming.success(Continue)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }

    it("should do onError only after all the queue was drained, test2") {
      var sum = 0L
      val complete = new CountDownLatch(1)

      val buffer = BufferedObserver(new Observer[Long] {
        def onNext(elem: Long) = {
          sum += elem
          Continue
        }
        def onError(ex: Throwable) = complete.countDown()
        def onComplete() = throw new IllegalStateException()
      }, BackPressured(10000))

      (0 until 9999).foreach(x => buffer.onNext(x))
      buffer.onError(new RuntimeException)

      assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    }
  }
}
