/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import scala.util.{Failure, Success}
import monix.eval.Task.{Async, Error, Now}
import monix.execution.CancelableFuture
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

object TaskFromTrySuite extends BaseTestSuite {
  test("Task.fromTry should return a Now with a Success") { implicit s =>
    val t = Task.fromTry(Success(10))
    assert(t.isInstanceOf[Now[_]])
  }

  test("Task.fromTry should succeed with a Success") { implicit s =>
    val t = Task.fromTry(Success(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromTry should return an Error with a Failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromTry(Failure(dummy))
    assert(t.isInstanceOf[Error[_]])
  }

  test("Task.fromTry should fail with a Failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromTry(Failure(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromTry with a Success should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromTry(Success(1))
    for (_ <- 0 until count) result = result.flatMap(_ => Task.fromTry(Success(1)))
    val f: CancelableFuture[Int] = result.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.fromTry with a Failure should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromTry(Failure(DummyException("dummy")))
    for (_ <- 0 until count) result = result.flatMap(_ => Task.fromTry(Failure(DummyException("dummy"))))
    val f: CancelableFuture[Nothing] = result.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(DummyException("dummy"))))
  }

  test("Task.fromTryL should return an Async with a Success") { implicit s =>
    val t = Task.fromTryL(Success(10))
    assert(t.isInstanceOf[Async[_]])
  }

  test("Task.fromTryL should succeed with a Success") { implicit s =>
    val t = Task.fromTryL(Success(10))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromTryL should return an Async with a Failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromTryL(Failure(dummy))
    s.tick()
    assert(t.isInstanceOf[Async[_]])
  }

  test("Task.fromTryL should fail with a Failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromTryL(Failure(dummy))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromTryL with a Success should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromTryL(Success(1))
    for (_ <- 0 until count) result = result.flatMap(_ => Task.fromTryL(Success(1)))
    val f: CancelableFuture[Int] = result.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.fromTryL with a Failure should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromTryL(Failure(DummyException("dummy")))
    for (_ <- 0 until count) result = result.flatMap(_ => Task.fromTryL(Failure(DummyException("dummy"))))
    val f: CancelableFuture[Nothing] = result.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(DummyException("dummy"))))
  }

  test("Task.fromTryL with a Success should behave identically to Task(someTry).dematerialize") { implicit s =>
    val ftl = Task.fromTryL(Success(1))
    val mdm = Task(Success(1)).dematerialize
    val f1: CancelableFuture[Int] = ftl.runToFuture
    val f2: CancelableFuture[Int] = mdm.runToFuture
    s.tick()
    assertEquals(f1.value, f2.value)
  }

  test("Task.fromTryL with a Failure should behave identically to Task(someTry).dematerialize") { implicit s =>
    val ftl = Task.fromTryL(Failure(DummyException("dummy")))
    val mdm = Task(Failure(DummyException("dummy"))).dematerialize
    val f1: CancelableFuture[Int] = ftl.runToFuture
    val f2: CancelableFuture[Int] = mdm.runToFuture
    s.tick()
    assertEquals(f1.value, f2.value)
  }

  test("Task.fromTryL catches evaluation exceptions") { implicit s =>
    val breaksEverything = Task.fromTryL(throw DummyException("outside of try"))
    val f = breaksEverything.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(DummyException("outside of try"))))
  }
}
