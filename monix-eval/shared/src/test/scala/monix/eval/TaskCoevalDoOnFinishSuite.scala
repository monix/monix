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

package monix.eval

import monix.execution.exceptions.DummyException

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object TaskCoevalDoOnFinishSuite extends BaseTestSuite {
  test("Task.doOnFinish should work for successful values") { implicit s =>
    val p = Promise[Option[Throwable]]()

    val task = Task(10).doOnFinish(s => Task(p.success(s)))
    val f = task.runAsync; s.tick()

    assertEquals(f.value, Some(Success(10)))
    assertEquals(p.future.value, Some(Success(None)))
  }

  test("Task.doOnFinish should work for failures values") { implicit s =>
    val ex = DummyException("dummy")
    val p = Promise[Option[Throwable]]()

    val task = Task.raiseError[Int](ex).doOnFinish(s => Task(p.success(s)))
    val f = task.runAsync; s.tick()

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(p.future.value, Some(Success(Some(ex))))
  }

  test("Coeval.doOnFinish should work for successful values") { implicit s =>
    val p = Promise[Option[Throwable]]()

    val coeval = Coeval(10).doOnFinish(s => Coeval(p.success(s)))
    val result = coeval.runTry

    assertEquals(result, Success(10))
    assertEquals(p.future.value, Some(Success(None)))
  }

  test("Coeval.doOnFinish should work for failures values") { implicit s =>
    val ex = DummyException("dummy")
    val p = Promise[Option[Throwable]]()

    val coeval = Coeval.raiseError[Int](ex).doOnFinish(s => Coeval(p.success(s)))
    val result = coeval.runTry

    assertEquals(result, Failure(ex))
    assertEquals(p.future.value, Some(Success(Some(ex))))
  }
}
