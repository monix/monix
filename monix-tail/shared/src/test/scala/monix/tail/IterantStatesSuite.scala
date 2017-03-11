/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.tail

import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantStatesSuite extends BaseTestSuite {
  test("AsyncStream.suspend(Task(list))") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(AsyncStream.fromSeq[Int](list))
    val result = AsyncStream.suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("LazyStream.suspend(Coeval(list))") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(LazyStream.fromSeq[Int](list))
    val result = LazyStream.suspend(deferred).toListL.runTry
    assertEquals(result, Success(list))
  }

  test("AsyncStream.suspend(AsyncStream)") { implicit s =>
    val list = List(1,2,3)
    val deferred = AsyncStream.fromSeq[Int](list)
    val result = AsyncStream.suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("LazyStream.suspend(LazyStream)") { implicit s =>
    val list = List(1,2,3)
    val deferred = LazyStream.fromSeq[Int](list)
    val result = LazyStream.suspend(deferred).toListL.runTry
    assertEquals(result, Success(list))
  }

  test("AsyncStream.next") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(AsyncStream.fromSeq[Int](list))
    val result = AsyncStream.nextS(0, deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("LazyStream.next") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(LazyStream.fromSeq[Int](list))
    val result = LazyStream.nextS(0, deferred, Coeval.unit).toListL.runTry
    assertEquals(result, Success(0 :: list))
  }

  test("AsyncStream.nextSeq") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(AsyncStream.fromSeq[Int](list))
    val result = AsyncStream.nextSeqS(List(0).iterator, deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("LazyStream.nextSeq") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(LazyStream.fromSeq[Int](list))
    val result = LazyStream.nextSeqS(List(0).iterator, deferred, Coeval.unit).toListL.runTry
    assertEquals(result, Success(0 :: list))
  }

  test("AsyncStream.halt(None)") { implicit s =>
    val result = AsyncStream.haltS(None).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(Nil)))
  }

  test("LazyStream.halt(None)") { implicit s =>
    val result = LazyStream.haltS(None).toListL.runTry
    assertEquals(result, Success(Nil))
  }

  test("AsyncStream.halt(Some(ex))") { implicit s =>
    val ex = DummyException("dummy")
    val result = AsyncStream.haltS(Some(ex)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(ex)))
  }

  test("LazyStream.halt(Some(ex))") { implicit s =>
    val ex = DummyException("dummy")
    val result = LazyStream.haltS(Some(ex)).toListL.runTry
    assertEquals(result, Failure(ex))
  }
}

