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

package monix.eval

import monix.eval.Task.{ Error, Now }
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.util.{ Failure, Success }

object TaskFromEitherSuite extends BaseTestSuite {
  test("Task.fromEither (`E <: Throwable` version) should returns a Now with a Right") { _ =>
    val t = Task.fromEither(Right(10))
    assert(t.isInstanceOf[Now[_]])
  }

  test("Task.fromEither (`E <: Throwable` version) should succeed with a Right") { implicit s =>
    val t = Task.fromEither(Right(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromEither (`E <: Throwable` version) should returns an Error with a Left") { _ =>
    val dummy = DummyException("dummy")
    val t = Task.fromEither(Left(dummy))
    assert(t.isInstanceOf[Error[_]])
  }

  test("Task.fromEither (`E <: Throwable` version) should fail with a Left") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromEither(Left(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromEither (`E <: Throwable` version) with a Right should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromEither(Right(1)).runToFuture
    for (_ <- 0 until count) result = Task.fromEither(Right(1)).runToFuture
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.fromEither (`E <: Throwable` version) with a Left should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromEither(Left(DummyException("dummy"))).runToFuture
    for (_ <- 0 until count) result = Task.fromEither(Left(DummyException("dummy"))).runToFuture
    assertEquals(result.value, Some(Failure(DummyException("dummy"))))
  }

  test("Task.fromEither (free `E` version) should returns a Now with a Right") { _ =>
    val t = Task.fromEither(DummyException(_))(Right(10))
    assert(t.isInstanceOf[Now[_]])
  }

  test("Task.fromEither (free `E` version) should succeed with a Right") { implicit s =>
    val t = Task.fromEither(DummyException(_))(Right(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromEither (free `E` version) should returns an Error with a Left") { _ =>
    val t = Task.fromEither(DummyException(_))(Left("dummy"))
    assert(t.isInstanceOf[Error[_]])
  }

  test("Task.fromEither (free `E` version) should fail with a Left") { implicit s =>
    val t = Task.fromEither(DummyException(_))(Left("dummy"))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(DummyException("dummy"))))
  }

  test("Task.fromEither (free `E` version) with a Right should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromEither(DummyException(_))(Right(1)).runToFuture
    for (_ <- 0 until count) result = Task.fromEither(DummyException(_))(Right(1)).runToFuture
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.fromEither (free `E` version) with a Left should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.fromEither(DummyException(_))(Left("dummy")).runToFuture
    for (_ <- 0 until count) result = Task.fromEither(DummyException(_))(Left("dummy")).runToFuture
    assertEquals(result.value, Some(Failure(DummyException("dummy"))))
  }
}
