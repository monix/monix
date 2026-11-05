/*
 * Copyright (c) 2014-2022 Monix Contributors.
 * See the project homepage at: https://monix.io
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

package monix.execution

import java.util.concurrent.{ CancellationException, CountDownLatch, Executors, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import minitest.SimpleTestSuite
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutorService, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object CancelableFutureCancellationJVMSuite extends SimpleTestSuite {
  private val timeoutSeconds = 5L

  private def await(latch: CountDownLatch): Unit =
    assert(latch.await(timeoutSeconds, TimeUnit.SECONDS))

  private def withExecutionContext[A](f: (ExecutionContextExecutorService) => A): A = {
    val executor = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
    try f(executor)
    finally {
      executor.shutdownNow()
      assert(executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
    }
  }

  test("fromFuture cancellation races source completion without hanging") {
    withExecutionContext { executor =>
      var index = 0
      while (index < 100) {
        val source = Promise[Int]()
        val adapted = CancelableFuture.fromFuture(source.future)
        val start = new CountDownLatch(1)
        val cancelDone = new CountDownLatch(1)
        val completeDone = new CountDownLatch(1)

        executor.execute { () =>
          await(start)
          adapted.cancel()
          cancelDone.countDown()
        }
        executor.execute { () =>
          await(start)
          val _ = source.trySuccess(index)
          completeDone.countDown()
        }

        start.countDown()
        await(cancelDone)
        await(completeDone)
        Await.ready(adapted, timeoutSeconds.seconds)
        adapted.value match {
          case Some(Success(value)) => assertEquals(value, index)
          case Some(Failure(_: CancellationException)) => ()
          case other => fail(s"Unexpected adapter result: $other")
        }
        index += 1
      }
    }
  }

  test("CancelablePromise cancellation races completion without affecting other subscribers") {
    withExecutionContext { executor =>
      var index = 0
      while (index < 100) {
        val source = CancelablePromise[Int]()
        val cancelledSubscription = source.future
        val otherSubscription = source.future
        val start = new CountDownLatch(1)
        val cancelDone = new CountDownLatch(1)
        val completeDone = new CountDownLatch(1)
        val producerError = new AtomicReference[Throwable]()

        executor.execute { () =>
          await(start)
          cancelledSubscription.cancel()
          cancelDone.countDown()
        }
        executor.execute { () =>
          await(start)
          try {
            val _ = source.tryComplete(Success(index))
          } catch { case error: Throwable => producerError.set(error) }
          completeDone.countDown()
        }

        start.countDown()
        await(cancelDone)
        await(completeDone)
        Await.ready(cancelledSubscription, timeoutSeconds.seconds)
        assertEquals(Await.result(otherSubscription, timeoutSeconds.seconds), index)
        assertEquals(producerError.get(), null)
        cancelledSubscription.value match {
          case Some(Success(value)) => assertEquals(value, index)
          case Some(Failure(_: CancellationException)) => ()
          case other => fail(s"Unexpected subscription result: $other")
        }
        index += 1
      }
    }
  }

  test("zip cancellation waits for both concurrently signaled inputs") {
    withExecutionContext { executor =>
      implicit val ec: ExecutionContext = executor
      val leftCallback = new AtomicReference[CompletionCallback[Int]]()
      val rightCallback = new AtomicReference[CompletionCallback[Int]]()
      val leftRegistered = new CountDownLatch(1)
      val rightRegistered = new CountDownLatch(1)
      val leftCancellationRequested = new CountDownLatch(1)
      val rightCancellationRequested = new CountDownLatch(1)
      val leftCanceled = new AtomicInteger(0)
      val rightCanceled = new AtomicInteger(0)

      val left = CancelableFuture.fromAsync[Int] { callback =>
        leftCallback.set(callback)
        leftRegistered.countDown()
        Cancelable(() => {
          val _ = leftCanceled.incrementAndGet()
          leftCancellationRequested.countDown()
          ()
        })
      }
      val right = CancelableFuture.fromAsync[Int] { callback =>
        rightCallback.set(callback)
        rightRegistered.countDown()
        Cancelable(() => {
          val _ = rightCanceled.incrementAndGet()
          rightCancellationRequested.countDown()
          ()
        })
      }
      await(leftRegistered)
      await(rightRegistered)

      val zipped = left.zip(right)
      zipped.cancel()
      await(leftCancellationRequested)
      await(rightCancellationRequested)
      assertEquals(leftCanceled.get(), 1)
      assertEquals(rightCanceled.get(), 1)
      assertEquals(zipped.value, None)

      val callbacksReady = new CountDownLatch(2)
      val releaseCallbacks = new CountDownLatch(1)
      val callbacksDone = new CountDownLatch(2)
      executor.execute { () =>
        callbacksReady.countDown()
        await(releaseCallbacks)
        leftCallback.get().onCancellation()
        callbacksDone.countDown()
      }
      executor.execute { () =>
        callbacksReady.countDown()
        await(releaseCallbacks)
        rightCallback.get().onCancellation()
        callbacksDone.countDown()
      }

      await(callbacksReady)
      assertEquals(zipped.value, None)
      releaseCallbacks.countDown()
      await(callbacksDone)
      Await.ready(zipped, timeoutSeconds.seconds)
      assert(zipped.value.exists { case Failure(_: CancellationException) => true; case _ => false })
      assertEquals(leftCanceled.get(), 1)
      assertEquals(rightCanceled.get(), 1)
    }
  }

  test("cancellation during a flatMap handoff reaches the inner stage and waits") {
    withExecutionContext { executor =>
      implicit val ec: ExecutionContext = executor
      val sourceCallback = new AtomicReference[CompletionCallback[Int]]()
      val innerCallback = new AtomicReference[CompletionCallback[Int]]()
      val sourceRegistered = new CountDownLatch(1)
      val innerRegistered = new CountDownLatch(1)
      val handoffEntered = new CountDownLatch(1)
      val releaseHandoff = new CountDownLatch(1)
      val innerCanceled = new CountDownLatch(1)
      val innerCancelCount = new AtomicInteger(0)

      val source = CancelableFuture.fromAsync[Int] { callback =>
        sourceCallback.set(callback)
        sourceRegistered.countDown()
        Cancelable.empty
      }
      val inner = CancelableFuture.fromAsync[Int] { callback =>
        innerCallback.set(callback)
        innerRegistered.countDown()
        Cancelable(() => {
          innerCancelCount.incrementAndGet()
          innerCanceled.countDown()
        })
      }
      await(sourceRegistered)
      await(innerRegistered)

      val flattened = source.flatMap { _ =>
        handoffEntered.countDown()
        await(releaseHandoff)
        inner
      }
      executor.execute { () => sourceCallback.get().onSuccess(1) }
      await(handoffEntered)

      flattened.cancel()
      releaseHandoff.countDown()
      await(innerCanceled)
      assertEquals(innerCancelCount.get(), 1)
      assertEquals(flattened.value, None)

      innerCallback.get().onCancellation()
      Await.ready(flattened, timeoutSeconds.seconds)
      assert(flattened.value.exists { case Failure(_: CancellationException) => true; case _ => false })
    }
  }
}
