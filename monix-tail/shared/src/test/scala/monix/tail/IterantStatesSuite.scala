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

package monix.tail

import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.tail.batches.BatchCursor

import scala.util.{Failure, Success}

object IterantStatesSuite extends BaseTestSuite {
  test("Iterant[Task].suspend(Task(list))") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant[Task].fromSeq[Int](list))
    val result = Iterant[Task].suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("Iterant[Coeval].suspend(Coeval(list))") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(Iterant[Coeval].fromSeq[Int](list))
    val result = Iterant[Coeval].suspend(deferred).toListL.runTry
    assertEquals(result, Success(list))
  }

  test("Iterant[Task].suspend(AsyncStream)") { implicit s =>
    val list = List(1,2,3)
    val deferred = Iterant[Task].fromSeq[Int](list)
    val result = Iterant[Task].suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("Iterant[Coeval].suspend(LazyStream)") { implicit s =>
    val list = List(1,2,3)
    val deferred = Iterant[Coeval].fromSeq[Int](list)
    val result = Iterant[Coeval].suspend(deferred).toListL.runTry
    assertEquals(result, Success(list))
  }

  test("Iterant[Task].next") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant[Task].fromSeq[Int](list))
    val result = Iterant[Task].nextS(0, deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("Iterant[Coeval].next") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(Iterant[Coeval].fromSeq[Int](list))
    val result = Iterant[Coeval].nextS(0, deferred, Coeval.unit).toListL.runTry
    assertEquals(result, Success(0 :: list))
  }

  test("Iterant[Task].nextCursor") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant[Task].fromSeq[Int](list))
    val result = Iterant[Task].nextCursorS(BatchCursor(0), deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("Iterant[Coeval].nextCursor") { implicit s =>
    val list = List(1,2,3)
    val deferred = Coeval.eval(Iterant[Coeval].fromSeq[Int](list))
    val result = Iterant[Coeval].nextCursorS(BatchCursor(0), deferred, Coeval.unit).toListL.runTry
    assertEquals(result, Success(0 :: list))
  }

  test("Iterant[Task].halt(None)") { implicit s =>
    val result = Iterant[Task].haltS(None).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(Nil)))
  }

  test("Iterant[Coeval].halt(None)") { implicit s =>
    val result = Iterant[Coeval].haltS(None).toListL.runTry
    assertEquals(result, Success(Nil))
  }

  test("Iterant[Task].halt(Some(ex))") { implicit s =>
    val ex = DummyException("dummy")
    val result = Iterant[Task].haltS(Some(ex)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(ex)))
  }

  test("Iterant[Coeval].halt(Some(ex))") { implicit s =>
    val ex = DummyException("dummy")
    val result = Iterant[Coeval].haltS(Some(ex)).toListL.runTry
    assertEquals(result, Failure(ex))
  }
}

