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

package monix.eval

import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object IterantStatesSuite extends BaseTestSuite {
  test("Iterant.suspend(Task(list))") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant.fromSeq[Int](list))
    val result = Iterant.suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("Iterant.suspend(Iterant)") { implicit s =>
    val list = List(1,2,3)
    val deferred = Iterant.fromSeq[Int](list)
    val result = Iterant.suspend(deferred).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(list)))
  }

  test("Iterant.next") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant.fromSeq[Int](list))
    val result = Iterant.nextS(0, deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("Iterant.nextSeq") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant.fromSeq[Int](list))
    val result = Iterant.nextSeqS(List(0).iterator, deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }

  test("Iterant.nextGen") { implicit s =>
    val list = List(1,2,3)
    val deferred = Task.eval(Iterant.fromSeq[Int](list))
    val result = Iterant.nextGenS(List(0), deferred, Task.unit).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0 :: list)))
  }


  test("Iterant.halt(None)") { implicit s =>
    val result = Iterant.haltS(None).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(Nil)))
  }

  test("Iterant.halt(Some(ex))") { implicit s =>
    val ex = DummyException("dummy")
    val result = Iterant.haltS(Some(ex)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(ex)))
  }
}
