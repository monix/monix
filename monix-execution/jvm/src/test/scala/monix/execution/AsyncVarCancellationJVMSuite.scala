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

import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }
import minitest.SimpleTestSuite
import scala.util.Success

object AsyncVarCancellationJVMSuite extends SimpleTestSuite {
  private val timeoutSeconds = 5L

  private def await(latch: CountDownLatch): Unit =
    assert(latch.await(timeoutSeconds, TimeUnit.SECONDS))

  test("cancellation does not win after put selects a take") {
    val av = AsyncVar.empty[Int]()
    val readEntered = new CountDownLatch(1)
    val releaseRead = new CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor()
    var readValue: Option[Int] = None

    try {
      val readCallback = new CompletionCallback[Int] {
        def onSuccess(value: Int): Unit = {
          readValue = Some(value)
          readEntered.countDown()
          if (!releaseRead.await(timeoutSeconds, TimeUnit.SECONDS))
            throw new AssertionError("timed out waiting to release read callback")
        }

        def onError(error: Throwable): Unit = throw error

        def onCancellation(): Unit =
          throw new AssertionError("read callback was cancelled")
      }
      val _ = av.readByCallback(readCallback)
      val take = av.take()
      val put = executor.submit(new Runnable {
        def run(): Unit = {
          val _ = av.put(42)
        }
      })

      await(readEntered)
      take.cancel()
      assertEquals(take.value, None)

      releaseRead.countDown()
      val _ = put.get(timeoutSeconds, TimeUnit.SECONDS)
      assertEquals(take.value, Some(Success(42)))
      assertEquals(readValue, Some(42))
    } finally {
      releaseRead.countDown()
      executor.shutdownNow()
      assert(executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
    }
  }
}
