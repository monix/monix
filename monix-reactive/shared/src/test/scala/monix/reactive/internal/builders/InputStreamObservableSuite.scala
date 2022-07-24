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

package monix.reactive.internal.builders

import java.io.{ ByteArrayInputStream, InputStream }

import monix.execution.BaseTestSuite
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, BatchedExecution, SynchronousExecution }
import monix.execution.exceptions.{ APIContractViolationException, DummyException }
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.scalacheck.{ Gen, Prop }

import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Random, Success }

class InputStreamObservableSuite extends BaseTestSuite {
  test("fromInputStreamUnsafe yields a single subscriber observable") {
    implicit val s = TestScheduler()
    var errorThrown: Throwable = null
    val obs = Observable.fromInputStreamUnsafe(new ByteArrayInputStream(randomByteArray()))
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

  test("fromInputStreamUnsafe should throw if the chunkSize is zero") {
    val byteArray = randomByteArray()
    val in = new ByteArrayInputStream(byteArray)
    val error = intercept[IllegalArgumentException] {
      Observable.fromInputStreamUnsafe(in, 0)
      ()
    }
    assert(error.getMessage.contains("chunkSize"))
  }

  test("fromInputStreamUnsafe should throw if the chunkSize is negative") {
    val byteArray = randomByteArray()
    val in = new ByteArrayInputStream(byteArray)
    val error = intercept[IllegalArgumentException] {
      Observable.fromInputStreamUnsafe(in, -1)
      ()
    }
    assert(error.getMessage.contains("chunkSize"))
  }

  test("fromInputStreamUnsafe works for BatchedExecution") {
    implicit val s = TestScheduler(BatchedExecution(1024))
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val result = Observable
      .fromInputStreamUnsafe(in, 40)
      .foldLeft(Array.empty[Byte])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(_.toList))

    s.tick()
    assertEquals(result.value, Some(Success(Some(array.toList))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStreamUnsafe works for AlwaysAsyncExecution") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val result = Observable
      .fromInputStreamUnsafe(in, 40)
      .foldLeft(Array.empty[Byte])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(_.toList))

    s.tick()
    assertEquals(result.value, Some(Success(Some(array.toList))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStreamUnsafe works for SynchronousExecution") {
    implicit val s = TestScheduler(SynchronousExecution)

    var wasCompleted = 0
    val received = ListBuffer.empty[Byte]
    val array = randomByteArray()
    val in = new ByteArrayInputStream(array)

    val obs: Observable[Array[Byte]] = Observable
      .fromInputStreamUnsafe(in)
      .foldLeft(Array.empty[Byte])(_ ++ _)

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
    s.tick()

    assertEquals(received.toList, array.toList)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream does not block on initial execution") {
    implicit val s = TestScheduler()
    var didRead = false
    val is = new InputStream {
      def read(): Int = {
        didRead = true
        -1
      }
    }
    // Should not fail without s.tick()
    Observable.fromInputStreamUnsafe(is).foreach(_ => ())
    assert(!didRead)
  }

  test("fromInputStream closes the file handle onComplete") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val in = randomInputWithOnFinish(() => wasClosed = true)
    val f = Observable.fromInputStreamF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream closes the file handle onError on first call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 1, () => wasClosed = true)
    val f = Observable.fromInputStreamF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream closes the file handle onError on second call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 2, () => wasClosed = true)
    val f = Observable.fromInputStreamF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "InputStream should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream closes the file handle on cancel") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)

    var wasClosed = false
    val in = randomInputWithOnFinish(() => wasClosed = true)
    val f = Observable.fromInputStreamF(Task(in)).completedL.runToFuture

    s.tickOne()
    f.cancel()
    s.tick()

    assert(wasClosed, "InputStream should have been closed")
    assertEquals(s.state.lastReportedError, null)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream should signal an error if the chunkSize is zero or negative") {
    implicit val s = TestScheduler()

    val byteArray = randomByteArray()
    val in = new ByteArrayInputStream(byteArray)
    val f = Observable.fromInputStream(Task(in), 0).completedL.runToFuture

    s.tick()

    intercept[IllegalArgumentException] {
      f.value.get.get
      ()
    }
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream completes normally for files with size == 0") {
    implicit val s = TestScheduler()

    val byteArray = new Array[Byte](0)
    val in = new ByteArrayInputStream(byteArray)
    val f = Observable
      .fromInputStream(Task(in))
      .map(_.length)
      .sumL
      .runToFuture

    s.tick()

    assertEquals(f.value.get.get, 0)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromInputStream fills the buffer up to 'chunkSize' if possible") {
    implicit val s = TestScheduler()

    val gen = for {
      byteSize  <- Gen.choose(1, 4096)
      chunkSize <- Gen.choose(Math.floorDiv(byteSize, 2).max(1), byteSize * 2)
    } yield {
      (byteSize, chunkSize)
    }

    check {
      Prop.forAllNoShrink(gen) { // do not shrink to avoid a zero for the chunkSize
        case (byteSize, chunkSize) =>
          val byteArray = randomByteArray(byteSize, 10)
          val forcedReadSize = Math.floorDiv(byteSize, 10).max(1) // avoid zero-byte reads
          val in = inputWithStaggeredBytes(forcedReadSize, new ByteArrayInputStream(byteArray))
          val f = Observable
            .fromInputStreamF(Task(in), chunkSize)
            .foldLeftL(Vector.empty[Int]) {
              case (acc, byteArray) =>
                acc :+ byteArray.length
            }
            .runToFuture

          s.tick()

          val resultChunkSizes = f.value.get.get
          if (byteArray.length > chunkSize) // all values except the last should be equal to the chunkSize
            resultChunkSizes.init.forall(_ == chunkSize) && resultChunkSizes.last <= chunkSize
          else
            resultChunkSizes.head <= chunkSize
      }
    }
  }

  def inputWithStaggeredBytes(forcedReadSize: Int, underlying: ByteArrayInputStream): InputStream = {
    new InputStream {
      override def read(): Int = underlying.read()
      override def read(b: Array[Byte]): Int =
        read(b, 0, forcedReadSize)
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        underlying.read(b, off, forcedReadSize.min(len))
    }
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
      override def close(): Unit = {
        underlying.close()
        onFinish()
      }
    }
  }

  def randomByteArray(size: Int = 2048, nMinSize: Int = 0): Array[Byte] = {
    val length = Random.nextInt(size).max(nMinSize)
    val bytes = new Array[Byte](length)
    Random.nextBytes(bytes)
    bytes
  }
}
