/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import cats.effect.ExitCase
import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution.exceptions.APIContractViolationException
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber
import org.scalacheck.{Gen, Prop}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Random, Success}

object CharsReaderObservableSuite extends SimpleTestSuite with Checkers {
  test("fromCharsReaderUnsafe yields a single subscriber observable") {
    implicit val s = TestScheduler()
    var errorThrown: Throwable = null
    val obs = Observable.fromCharsReaderUnsafe(new StringReader(randomString()))
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

  test("fromCharsReaderUnsafe should throw if the chunkSize is zero") {
    val str = randomString()
    val in = new StringReader(str)

    val error = intercept[IllegalArgumentException] {
      Observable.fromCharsReaderUnsafe(in, 0)
      ()
    }
    assert(error.getMessage.contains("chunkSize"))
  }

  test("fromCharsReaderUnsafe should throw if the chunkSize is negative") {
    val str = randomString()
    val in = new StringReader(str)

    val error = intercept[IllegalArgumentException] {
      Observable.fromCharsReaderUnsafe(in, -1)
      ()
    }
    assert(error.getMessage.contains("chunkSize"))
  }

  test("fromCharsReaderUnsafe works for BatchedExecution") {
    implicit val s = TestScheduler(BatchedExecution(1024))
    val string = randomString()
    val in = new StringReader(string)

    val result = Observable
      .fromCharsReaderUnsafe(in, 40)
      .foldLeft(Array.empty[Char])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(arr => new String(arr)))

    s.tick()
    assertEquals(result.value, Some(Success(Some(string))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReaderUnsafe works for AlwaysAsyncExecution") {
    implicit val s = TestScheduler(AlwaysAsyncExecution)
    val string = randomString()
    val in = new StringReader(string)

    val result = Observable
      .fromCharsReaderUnsafe(in, 40)
      .foldLeft(Array.empty[Char])(_ ++ _)
      .runAsyncGetFirst
      .map(_.map(arr => new String(arr)))

    s.tick()
    assertEquals(result.value, Some(Success(Some(string))))
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReaderUnsafe works for SynchronousExecution") {
    implicit val s = TestScheduler(SynchronousExecution)

    var wasCompleted = 0
    val received = ArrayBuffer.empty[Char]
    val string = randomString()
    val in = new StringReader(string)

    val obs: Observable[Array[Char]] = Observable
      .fromCharsReaderUnsafe(in)
      .foldLeft(Array.empty[Char])(_ ++ _)

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

    s.tick()

    assertEquals(new String(received.toArray), string)
    assertEquals(wasCompleted, 1)
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReader closes the file handle onComplete") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val in = randomReaderWithOnFinish(() => wasClosed = true)
    val f = Observable.fromCharsReaderF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReader closes the file handle onError on first call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 1, () => wasClosed = true)
    val f = Observable.fromCharsReaderF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReader closes the file handle onError on second call") {
    implicit val s = TestScheduler()

    var wasClosed = false
    val ex = DummyException("dummy")
    val in = inputWithError(ex, 2, () => wasClosed = true)
    val f = Observable.fromCharsReaderF(Task(in)).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assert(wasClosed, "Reader should have been closed")
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReader closes the file handle on cancel") {
    for (_ <- 0 until 100) {
      import scala.concurrent.duration._
      implicit val s = TestScheduler(AlwaysAsyncExecution)

      var wasClosed = false
      var wasCanceled = 0
      var wasStarted = false
      var wasCompleted = false

      val f = Observable
        .fromCharsReaderF(
          Task.pure(
            randomReaderWithOnFinish(() => wasClosed = true)
          ))
        .flatMap { _ =>
          Observable.suspend {
            wasStarted = true
            Observable.eval {
              assert(!wasClosed, "Resource should be available")
            }.delayExecution(1.second).guaranteeCase {
              case ExitCase.Canceled =>
                Task { wasCanceled += 1 }
              case _ =>
                Task { wasCompleted = true }
            }
          }
        }
        .doOnSubscriptionCancel(Task { wasCanceled += 2 })
        .completedL
        .runToFuture

      s.tick()
      f.cancel()
      s.tick()

      // Test needed because string could be empty
      if (wasStarted) {
        assertEquals(f.value, None)
        assert(!wasCompleted, "Task shouldn't have completed")
        assertEquals(wasCanceled, 3)
      }

      assert(wasClosed, "Reader should have been closed")
      assertEquals(s.state.lastReportedError, null)
      assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
    }
  }

  test("fromCharsReader does not block on initial execution") {
    implicit val s = TestScheduler()
    var didRead = false
    val reader = new Reader() {
      def read(cbuf: Array[Char], off: Int, len: Int): Int = {
        didRead = true
        -1
      }

      def close(): Unit = ()
    }
    // Should not fail without s.tick()
    Observable.fromCharsReaderUnsafe(reader).foreach(_ => ())
    assert(!didRead)
  }

  test("fromCharsReader signals an error if the chunk size is zero or negative") {
    implicit val s = TestScheduler()

    val str = randomString()
    val in = new StringReader(str)
    val f = Observable
      .fromCharsReader(Task(in), 0)
      .completedL
      .runToFuture

    s.tick()

    intercept[IllegalArgumentException] {
      f.value.get.get
      ()
    }
    assert(s.state.tasks.isEmpty, "should be left with no pending tasks")
  }

  test("fromCharsReader completes normally for files with size == 0") {
    implicit val s = TestScheduler()

    val in = new StringReader("")
    val f = Observable
      .fromCharsReader(Task(in))
      .map(_.length)
      .sumL
      .runToFuture

    s.tick()

    assertEquals(f.value.get.get, 0)
  }

  test("fromCharsReader fills the buffer up to 'chunkSize' if possible") {
    implicit val s = TestScheduler()

    val gen = for {
      nCharsPerLine <- Gen.choose(1, 150)
      nLines        <- Gen.choose(1, 250)
      chunkSize     <- Gen.choose(Math.floorDiv(nCharsPerLine, 2).max(1), nCharsPerLine * 2)
    } yield (nCharsPerLine, nLines, chunkSize)

    val prop = Prop
      .forAllNoShrink(gen) { // do not shrink to avoid a zero for the chunkSize
        case (nCharsPerLine, nLines, chunkSize) =>
          val str = randomString(nLines, 1, nCharsPerLine, 1)
          val forcedReadSize = Math.floorDiv(nCharsPerLine, 10).max(1) // avoid zero-char reads
          val in = inputWithStaggeredChars(forcedReadSize, new StringReader(str))
          val f = Observable
            .fromCharsReader(Task(in), chunkSize)
            .foldLeftL(Vector.empty[Int]) {
              case (acc, charArray) => acc :+ charArray.length
            }
            .runToFuture

          s.tick()

          val resultChunkSizes = f.value.get.get
          if (str.length > chunkSize) // all values except the last should be equal to the chunkSize
            resultChunkSizes.init.forall(_ == chunkSize) && resultChunkSizes.last <= chunkSize
          else resultChunkSizes.head <= chunkSize
      }
    check(prop)
  }

  def inputWithStaggeredChars(forcedReadSize: Int, underlying: StringReader): Reader = {
    new Reader {
      override def read(): Int = underlying.read()
      override def read(b: Array[Char]): Int =
        read(b, 0, forcedReadSize)
      override def read(b: Array[Char], off: Int, len: Int): Int =
        underlying.read(b, off, forcedReadSize.min(len))
      override def close(): Unit = underlying.close()
    }
  }

  def inputWithError(ex: Throwable, whenToThrow: Int, onFinish: () => Unit): Reader =
    new Reader {
      private[this] var callIdx = 0

      def read(cbuf: Array[Char], off: Int, len: Int): Int = {
        callIdx += 1
        if (callIdx == whenToThrow) throw ex
        else if (off < len) {
          cbuf(off) = 'a'; 1
        } else 0
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
      override def close(): Unit = {
        underlying.close()
        onFinish()
      }
    }
  }

  def randomString(
    nLines: Int = 100,
    nMinLines: Int = 0,
    nCharsPerLine: Int = 100,
    nMinCharsPerLine: Int = 0): String = {

    val chars = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toVector
    val builder = new StringBuilder
    val lines = Random.nextInt(nLines).max(nMinLines)

    for (_ <- 0 until lines) {
      val lineLength = Random.nextInt(nCharsPerLine).max(nMinCharsPerLine)
      val line = for (_ <- 0 until lineLength) yield chars(Random.nextInt(chars.length))
      builder.append(new String(line.toArray))
      builder.append('\n')
    }

    builder.toString()
  }
}
