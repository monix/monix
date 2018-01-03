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

package monix.reactive.internal.subscribers

import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Observable}

import scala.util.{Failure, Success}

object ObservableForeachSuite extends BaseTestSuite {
  test("foreach subscribes immediately") { scheduler =>
    implicit val s = scheduler.withExecutionModel(SynchronousExecution)

    var sum = 0
    val f = Observable.fromIterable(0 until 1000).foreach(x => sum += x)

    assertEquals(sum, 500 * 999)
    assertEquals(f.value, Some(Success(())))
  }

  test("foreachL subscribes immediately") { scheduler =>
    implicit val s = scheduler.withExecutionModel(SynchronousExecution)

    var sum = 0
    val f = Observable.fromIterable(0 until 1000).foreachL(x => sum += x).runAsync

    assertEquals(sum, 500 * 999)
    assertEquals(f.value, Some(Success(())))
  }

  test("foreach protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Observable.fromIterable(0 until 1000).foreach(_ => throw dummy)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("foreachL protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Observable.fromIterable(0 until 1000).foreachL(_ => throw dummy).runAsync
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
