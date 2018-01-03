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

import java.io.{Reader, StringReader}

import minitest.SimpleTestSuite
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution.exceptions.APIContractViolationException
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Random, Success}

object CharsReaderObservableSuite extends SimpleTestSuite {
  test("yields a single subscriber observable") {
    implicit val s = TestScheduler()
    var errorThrown: Throwable = null
    val obs = Observable.fromCharsReader(new StringReader(randomString()))
    obs.unsafeSubscribeFn(Subscriber.empty(s))
    s.tick()

    obs.unsafeSubscribeFn(new Subscriber[Array[Char]] {
      implicit val scheduler = s

      def onNext(elem: Array[Char]): Ack =
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
    val string = randomString()
    val in = new StringReader(string)

    val result = Observable.fromCharsReader(in, 40)
      .foldLeftF(Array.empty[Char])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(arr => new String(arr)))

    s.tick()
    assertEquals(result.value, Some(Success(Some(string))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("it works for AlwaysAsyncExecution") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)
    val string = randomString()
    val in = new StringReader(string)

    val result = Observable.fromCharsReader(in, 40)
      .foldLeftF(Array.empty[Char])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(arr => new String(arr)))

    s.tick()
    assertEquals(result.value, Some(Success(Some(string))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("it works for SynchronousExecution") {
    implicit val s = TestScheduler(SynchronousExecution)

    var wasCompleted = 0
    val received = ArrayBuffer.empty[Char]
    val string = randomString()
    val in = new StringReader(string)

    val obs: Observable[Array[Char]] = Observable
      .fromCharsReader(in)
      .foldLeftF(Array.empty[Char])(_ ++ _)

    obs.unsafeSubscribeFn(new Subscriber[Array[Char]] {
      implicit val scheduler = s

      def onError(ex: Throwable): Unit =
        throw new IllegalStateException("onError")
      def onComplete(): Unit =
        wasCompleted += 1

      def onNext(elem: Array[Char]): Ack = {
        received.appendAll(elem)
        Continue
      }
    })

    assertEquals(new String(received.toArray), string)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onComplete") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val in = randomReaderWithOnFinish(() => wasClosed = true)
    val f = Observable.fromCharsReader(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onError on first call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 1, () => wasClosed = true)
    val f = Observable.fromCharsReader(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle onError on second call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 2, () => wasClosed = true)
    val f = Observable.fromCharsReader(in).completedL.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("closes the file handle on cancel") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)

    var wasClosed = false
    val in = randomReaderWithOnFinish(() => wasClosed = true)
    val f = Observable.fromCharsReader(in).completedL.runAsync

    s.tickOne()
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(wasClosed, "Reader should have been closed")

    assertEquals(s.state.lastReportedError, null)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  def inputWithError(ex: Throwable, whenToThrow: Int, onFinish: () => Unit): Reader =
    new Reader {
      private[this] var callIdx = 0

      def read(cbuf: Array[Char], off: Int, len: Int): Int = {
        callIdx += 1
        if (callIdx == whenToThrow) throw ex
        else if (off < len) { cbuf(off) = 'a'; 1 }
        else 0
      }

      override def close(): Unit =
        onFinish()
    }

  def randomReaderWithOnFinish(onFinish: () => Unit): Reader = {
    val string = randomString()
    val underlying = new StringReader(string)
    new Reader {
      def read(cbuf: Array[Char], off: Int, len: Int): Int =
        underlying.read(cbuf, off, len)
      override def close(): Unit =
        onFinish()
    }
  }

  def randomString(): String = {
    val chars = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toVector
    val builder = new StringBuilder
    val lines = Random.nextInt(100)

    for (_ <- 0 until lines) {
      val lineLength = Random.nextInt(100)
      val line = for (_ <- 0 until lineLength) yield
        chars(Random.nextInt(chars.length))
      builder.append(new String(line.toArray))
      builder.append('\n')
    }

    builder.toString()
  }
}
