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

package monix.execution

import minitest.SimpleTestSuite
import monix.execution.exceptions.DummyException

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object CancelablePromiseSuite extends SimpleTestSuite {
  test("completes in success") {
    val p = CancelablePromise[Int]()
    assert(!p.isCompleted)

    val f1 = p.future
    val f2 = p.future
    val p3 = Promise[Int]()
    p.subscribe { v => p3.complete(v); () }

    assert(f1 ne f2, "f1 != f2")

    assert(p.tryComplete(Success(99)))
    assertEquals(f1.value, Some(Success(99)))
    assertEquals(f2.value, Some(Success(99)))
    assertEquals(p3.future.value, Some(Success(99)))

    assert(!p.tryComplete(Success(0)))
    assertEquals(p.future.value, Some(Success(99)))

    val p4 = Promise[Int]()
    p.subscribe { v => p4.complete(v); () }
    assertEquals(p4.future.value, Some(Success(99)))
  }

  test("completes in failure") {
    val p = CancelablePromise[Int]()
    assert(!p.isCompleted)

    val f1 = p.future
    val f2 = p.future
    val p3 = Promise[Int]()
    p.subscribe { v => p3.complete(v); () }

    assert(f1 ne f2, "f1 != f2")

    val dummy = DummyException("dummy")
    assert(p.tryComplete(Failure(dummy)))
    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Failure(dummy)))
    assertEquals(p3.future.value, Some(Failure(dummy)))

    assert(!p.tryComplete(Success(0)))
    assertEquals(p.future.value, Some(Failure(dummy)))

    val p4 = Promise[Int]()
    p.subscribe { v => p4.complete(v); () }
    assertEquals(p4.future.value, Some(Failure(dummy)))
  }

  test("can unsubscribe futures") {
    val p = CancelablePromise[Int]()

    val f1 = p.future
    val f2 = p.future
    val f3 = p.future

    f1.cancel()
    f3.cancel()
    p.complete(Success(99))

    assertEquals(f1.value, None)
    assertEquals(f2.value, Some(Success(99)))
    assertEquals(f3.value, None)
  }

  test("can unsubscribe callbacks") {
    val p = CancelablePromise[Int]()

    val p1 = Promise[Int]()
    val c1 = p.subscribe { v => p1.complete(v); () }

    val p2 = Promise[Int]()
    p.subscribe { v => p2.complete(v); () }

    val p3 = Promise[Int]()
    val c3 = p.subscribe { v => p3.complete(v); () }

    c1.cancel()
    c3.cancel()
    p.complete(Success(99))

    assertEquals(p1.future.value, None)
    assertEquals(p2.future.value, Some(Success(99)))
    assertEquals(p3.future.value, None)
  }

  test("already completed in success") {
    val p = CancelablePromise.successful(1)
    assertEquals(p.future.value, Some(Success(1)))
    assert(p.isCompleted)

    val p1 = Promise[Int]()
    p.subscribe { v => p1.complete(v); () }
    assertEquals(p1.future.value, Some(Success(1)))

    val f = p.future
    f.cancel()
    assertEquals(f.value, Some(Success(1)))

    assert(!p.tryComplete(Success(2)))
    assertEquals(p.future.value, Some(Success(1)))
  }

  test("already completed in failure") {
    val dummy = DummyException("dummy")
    val p = CancelablePromise.failed[Int](dummy)
    assertEquals(p.future.value, Some(Failure(dummy)))
    assert(p.isCompleted)

    val p1 = Promise[Int]()
    p.subscribe { v => p1.complete(v); () }
    assertEquals(p1.future.value, Some(Failure(dummy)))

    val f = p.future
    f.cancel()
    assertEquals(f.value, Some(Failure(dummy)))

    assert(!p.tryComplete(Success(2)))
    assertEquals(p.future.value, Some(Failure(dummy)))
  }
}
