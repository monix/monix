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

package monix.eval

import cats.effect.kernel.Async
import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, ExecutorService, Executors, ThreadFactory, TimeUnit }
import java.util.concurrent.atomic.AtomicReference
import minitest.SimpleTestSuite
import monix.execution.{ Callback, ExecutionModel, Scheduler }
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

object TaskAsyncResumeThreadJVMSuite extends SimpleTestSuite {
  test("a foreign async callback resumes on the evalOn scheduler") {
    val sourceThread = new AtomicReference[Thread]()
    val targetThread = new AtomicReference[Thread]()
    val producerThread = new AtomicReference[Thread]()
    val sourceExecutor = Executors.newSingleThreadExecutor(threadFactory("eval2-source", sourceThread))
    val targetExecutor = Executors.newSingleThreadExecutor(threadFactory("eval2-target", targetThread))
    val producerExecutor = Executors.newSingleThreadExecutor(threadFactory("eval2-producer", producerThread))
    val reportedErrors = new ConcurrentLinkedQueue[Throwable]()
    def executorContext(executor: ExecutorService): ExecutionContext =
      new ExecutionContext {
        override def execute(runnable: Runnable): Unit = executor.execute(runnable)

        override def reportFailure(cause: Throwable): Unit = {
          val _ = reportedErrors.add(cause)
        }
      }
    implicit val source: Scheduler =
      Scheduler(executorContext(sourceExecutor), ExecutionModel.AlwaysAsyncExecution)
    val targetScheduler = Scheduler(executorContext(targetExecutor), ExecutionModel.BatchedExecution(1024))
    val registered = new CountDownLatch(1)
    val callback = new AtomicReference[Callback[Throwable, Int]]()
    val resumedThread = new AtomicReference[Thread]()
    val resumedContext = new AtomicReference[ExecutionContext]()
    val resumedValue = new AtomicReference[Integer]()
    val F = Async[Task]
    val effect = F.evalOn(
      Task.async[Int] { cb =>
        callback.set(cb)
        registered.countDown()
      }.flatMap { value =>
        val _ = resumedThread.set(Thread.currentThread())
        F.executionContext.flatMap { ec =>
          Task.delay {
            resumedContext.set(ec)
            resumedValue.set(value)
          }
        }
      },
      targetScheduler
    )
    val result = effect.unsafeRunToFuture()

    try {
      assert(registered.await(5, TimeUnit.SECONDS), "async callback was not registered")
      val barrier = targetExecutor.submit(new Runnable {
        override def run(): Unit = ()
      })
      val _ = barrier.get(5, TimeUnit.SECONDS)
      val producerResult = producerExecutor.submit(new Runnable {
        override def run(): Unit = {
          producerThread.set(Thread.currentThread())
          callback.get().onSuccess(42)
        }
      })
      val _ = producerResult.get(5, TimeUnit.SECONDS)
      val _ = Await.result(result, 5.seconds)

      assert(resumedThread.get() eq targetThread.get())
      assert(resumedThread.get() ne producerThread.get())
      assert(sourceThread.get() ne targetThread.get())
      assert(resumedContext.get() eq targetScheduler)
      assertEquals(resumedValue.get().intValue(), 42)
      assert(reportedErrors.isEmpty)
    } finally {
      val _ = producerExecutor.shutdownNow()
      val _ = targetExecutor.shutdownNow()
      val _ = sourceExecutor.shutdownNow()
      val _ = producerExecutor.awaitTermination(5, TimeUnit.SECONDS)
      val _ = targetExecutor.awaitTermination(5, TimeUnit.SECONDS)
      val _ = sourceExecutor.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  private def threadFactory(name: String, ref: AtomicReference[Thread]): ThreadFactory =
    new ThreadFactory {
      override def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable, name)
        thread.setDaemon(true)
        ref.set(thread)
        thread
      }
    }
}
