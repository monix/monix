/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu

import minitest.TestSuite
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.schedulers.TestScheduler
import monifu.Ack.{Cancel, Continue}
import monifu.exceptions.DummyException
import monifu.internals._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


object InternalsSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty, "TestScheduler should be left with no pending tasks")
  }

  test("should do synchronous onContinueSignalComplete") { implicit s =>
    var wasCompleted = false

    Continue.onContinueSignalComplete(new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    assert(wasCompleted)
  }

  test("should do asynchronous onContinueSignalComplete") { implicit s =>
    var wasCompleted = false
    val p = Promise[Continue]()

    p.future.onContinueSignalComplete(new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    p.success(Continue)
    assert(!wasCompleted)

    s.tick()
    assert(wasCompleted)
  }

  test("should not signal complete if not continued") { implicit s =>
    var wasCompleted = false
    val p = Promise[Cancel]()

    Cancel.onContinueSignalComplete(new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    p.future.onContinueSignalComplete(new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    p.success(Cancel)

    s.tick()
    assert(!wasCompleted)
  }

  test("should do synchronous onContinueSignalError") { implicit s =>
    var wasCompleted = false
    val observer = new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      def onError(ex: Throwable) = {
        wasCompleted = true
      }
    }

    Continue.onContinueSignalError(observer, new RuntimeException())
    assert(wasCompleted)
  }

  test("should do asynchronous onContinueSignalError") { implicit s =>
    var wasCompleted = false
    val p = Promise[Continue]()
    val observer = new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      def onError(ex: Throwable) = {
        wasCompleted = true
      }
    }

    p.future.onContinueSignalError(observer, new RuntimeException())
    p.success(Continue)
    assert(!wasCompleted)

    s.tick()
    assert(wasCompleted)
  }

  test("should not signal error if not continued") { implicit s =>
    var wasCompleted = false
    val p = Promise[Cancel]()
    val observer = new Observer[Int] {
      def onNext(e: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      def onError(ex: Throwable) = {
        wasCompleted = true
      }
    }

    Cancel.onContinueSignalError(observer, new RuntimeException())
    p.future.onContinueSignalError(observer, new RuntimeException())
    p.success(Cancel)

    s.tick()
    assert(!wasCompleted)
  }

  test("should cancel cancelable in case Ack is a Cancel or a Failure") { implicit s =>
    val c1 = BooleanCancelable()
    Cancel.ifCanceledDoCancel(c1)
    assert(c1.isCanceled)

    val c2 = BooleanCancelable()
    Future(Cancel).ifCanceledDoCancel(c2)
    s.tick(); assert(c2.isCanceled)

    val c3 = BooleanCancelable()
    Future(throw new DummyException()).ifCanceledDoCancel(c3)
    s.tick(); assert(c3.isCanceled)

    val c4 = BooleanCancelable()
    Future.failed(new DummyException()).ifCanceledDoCancel(c4)
    assert(c4.isCanceled)
  }

  test("should not cancel a cancelable in case Ack is a Continue") { implicit s =>
    val c1 = BooleanCancelable()
    Continue.ifCanceledDoCancel(c1)
    s.tick(); assert(!c1.isCanceled)

    val c2 = BooleanCancelable()
    Future(Continue).ifCanceledDoCancel(c2)
    s.tick(); assert(!c2.isCanceled)

    val c3 = BooleanCancelable()
    Future.successful(Continue).ifCanceledDoCancel(c3)
    s.tick(); assert(!c3.isCanceled)
  }

  test("should do synchronous lightFastMap") { implicit s =>
    val r = Continue.fastFlatMap {
      case Continue => Cancel
      case Cancel => Continue
    }

    assertEquals(r.value, Some(Success(Cancel)))
  }

  test("should do asynchronous lightFastMap") { implicit s =>
    val r = Future(Continue).fastFlatMap {
      case Continue => Cancel
      case Cancel => Continue
    }

    assertEquals(r.value, None)
    s.tick()
    assertEquals(r.value, Some(Success(Cancel)))
  }

  test("should do synchronous lightFastMap with error") { implicit s =>
    val ex = DummyException("dummy")
    val source: Future[Ack] = Future.failed(ex)
    val r = source.fastFlatMap {
      case Continue => Cancel
      case Cancel => Continue
    }

    assertEquals(r.value, Some(Failure(ex)))
  }

  test("should do asynchronous lightFastMap with error") { implicit s =>
    val ex = DummyException("dummy")
    val source: Future[Ack] = Future(throw ex)
    val r = source.fastFlatMap {
      case Continue => Cancel
      case Cancel => Continue
    }

    assertEquals(r.value, None)
    s.tick()
    assertEquals(r.value, Some(Failure(ex)))
  }
}
