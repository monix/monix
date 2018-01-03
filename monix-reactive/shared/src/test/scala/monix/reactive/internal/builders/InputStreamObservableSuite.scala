/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.builders

import java.io.{ByteArrayInputStream, InputStream}

import minitest.SimpleTestSuite
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution.exceptions.APIContractViolationException
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Random, Success}

object InputStreamObservableSuite extends SimpleTestSuite {
  test("yields a single subscriber observable") {
    implicit val s = TestScheduler()
    var errorThrown: Throwable = null
    val obs = Observable.fromInputStream(new ByteArrayInputStream(randomByteArray()))
    obs.unsafeSubscribeFn(Subscriber.empty(s))
    s.tick()

    obs.unsafeSubscribeFn(new Subscriber[Array[Byte]] {
      implicit val scheduler = s

      def onNext(elem: Array[Byte]): Ack =
        throw new IllegalStateException("onNext")
      def onComplete(): Unit =
        throw new IllegalStateException("onComplete")
      def onError(ex: Throwable): Unit =
        errorThrown = ex
    })

    assert(errorThrown.isInstanceOf[APIContractViolationException])
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("it works for BatchedExecution") {
    implicit val s = TestScheduler(BatchedExecution(1024))
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val result = Observable.fromInputStream(in, 40)
      .foldLeftF(Array.empty[Byte])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(_.toList))

    s.tick()
    assertEquals(result.value, Some(Success(Some(array.toList))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("it works for AlwaysAsyncExecution") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val result = Observable.fromInputStream(in, 40)
      .foldLeftF(Array.empty[Byte])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(_.toList))

    s.tick()
    assertEquals(result.value, Some(Success(Some(array.toList))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("it works for SynchronousExecution") {
    implicit val s = TestScheduler(SynchronousExecution)

    var wasCompleted = 0
    val received = ListBuffer.empty[Byte]
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val obs: Observable[Array[Byte]] = Observable
      .fromInputStream(in)
      .foldLeftF(Array.empty[Byte])(_ ++ _)

    obs.unsafeSubscribeFn(new Subscriber[Array[Byte]] {
      implicit val scheduler = s

      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
      def onComplete(): Unit =
        wasCompleted += 1

      def onNext(elem: Array[Byte]): Ack = {
        received.appendAll(elem)
        Continue
      }
    })

    assertEquals(received.toList, array.toList)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onComplete") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val in = randomInputWithOnFinish(() => wasClosed = true)
    val f = Observable.fromInputStream(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onError on first call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 1, () => wasClosed = true)
    val f = Observable.fromInputStream(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onError on second call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 2, () => wasClosed = true)
    val f = Observable.fromInputStream(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle on cancel") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)

    var wasClosed = false
    val in = randomInputWithOnFinish(() => wasClosed = true)
    val f = Observable.fromInputStream(in).completedL.runAsync

    s.tickOne()
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(wasClosed, "InputStream should have been closed")

    assertEquals(s.state.lastReportedError, null)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  def inputWithError(ex: Throwable, whenToThrow: Int, onFinish: () => Unit): InputStream =
    new InputStream {
      private[this] var callIdx = 0

      def read(): Int = {
        callIdx += 1
        if (callIdx == whenToThrow) throw ex
        else 1
      }

      override def close(): Unit =
        onFinish()
    }

  def randomInputWithOnFinish(onFinish: () => Unit): InputStream = {
    val array = randomByteArray()
    val underlying = new ByteArrayInputStream(array)
    new InputStream {
      def read(): Int = underlying.read()

      override def read(b: Array[Byte]): Int =
        underlying.read(b)
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        underlying.read(b, off, len)
      override def close(): Unit =
        onFinish()
    }
  }

  def randomByteArray(): Array[Byte] = {
    val length = Random.nextInt(2048)
    val bytes = new Array[Byte](length)
    Random.nextBytes(bytes)
    bytes
  }
}
