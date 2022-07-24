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

import monix.execution.exceptions.DummyException

import java.util.concurrent.{ CompletableFuture, CompletionException }
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

class CompletableFutureConversionsSuite extends BaseTestSuite {

  fixture.test("FutureUtils.fromJavaCompletable works") { implicit s =>
    val cf = CompletableFuture.completedFuture(42)
    assertEquals(FutureUtils.fromJavaCompletable(cf).value, Some(Success(42)))
  }

  fixture.test("FutureUtils.fromJavaCompletable is non-terminating on cancelled source") { implicit s =>
    val cf = new CompletableFuture[Int]
    val sc = FutureUtils.fromJavaCompletable(cf)
    cf.cancel(true)
    assertEquals(sc.value, None)
  }

  fixture.test("FutureUtils.fromJavaCompletable reports errors") { implicit s =>
    val dummy = DummyException("dummy")
    val cf = new CompletableFuture[Int]
    cf.completeExceptionally(dummy)
    assertEquals(FutureUtils.fromJavaCompletable(cf).value, Some(Failure(dummy)))
  }

  fixture.test("FutureUtils.toJavaCompletable works") { implicit s =>
    val f = Future.successful(42)
    val cf = FutureUtils.toJavaCompletable(f)
    s.tickOne()
    assertEquals(cf.getNow(-1), 42)
  }

  fixture.test("FutureUtils.toJavaCompletable reports errors") { implicit s =>
    val dummy = DummyException("dummy")
    val ef = Future.failed[Int](dummy)
    val ecf = FutureUtils.toJavaCompletable(ef)
    s.tickOne()
    try {
      ecf.getNow(-1)
      fail("Should throw an error")
    } catch {
      case ex: CompletionException =>
        assertEquals(ex.getCause, dummy)
    }
  }

  fixture.test("CancelableFuture.fromJavaCompletable should work for success") { implicit s =>
    val cf = new CompletableFuture[Int]()
    val f = CancelableFuture.fromJavaCompletable(cf)

    cf.complete(1)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("CancelableFuture.fromJavaCompletable should work for failure") { implicit s =>
    val cf = new CompletableFuture[Int]()
    val f = CancelableFuture.fromJavaCompletable(cf)

    val dummy = DummyException("dummy")
    cf.completeExceptionally(dummy)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("CancelableFuture.fromJavaCompletable should be cancelable") { implicit s =>
    val cf = new CompletableFuture[Int]()
    val f = CancelableFuture.fromJavaCompletable(cf)

    f.cancel()
    assert(!cf.complete(1))
    assertEquals(f.value, None)
  }

  fixture.test("CancelableFuture.toJavaCompletable should work for success") { implicit s =>
    val p = CancelablePromise[Int]()
    val f = CancelableFuture.toJavaCompletable(p.future)

    p.success(1); s.tick()
    assertEquals(f.getNow(-1), 1)
  }

  fixture.test("CancelableFuture.toJavaCompletable should work for failure") { implicit s =>
    val p = CancelablePromise[Int]()
    val f = CancelableFuture.toJavaCompletable(p.future)

    val dummy = DummyException("dummy")
    p.failure(dummy)

    s.tick()
    try {
      f.getNow(-1)
      fail("should throw")
    } catch {
      case ex: CompletionException =>
        assertEquals(ex.getCause, dummy)
    }
  }

  fixture.test("CancelableFuture.toJavaCompletable should be cancelable") { implicit s =>
    val p = Promise[Int]()
    var wasCanceled = 0
    val source = CancelableFuture(p.future, Cancelable(() => wasCanceled += 1))
    val f = CancelableFuture.toJavaCompletable(source)

    f.cancel(false); s.tick()
    assertEquals(wasCanceled, 1)
    f.cancel(false); s.tick()
    assertEquals(wasCanceled, 1)
  }
}
