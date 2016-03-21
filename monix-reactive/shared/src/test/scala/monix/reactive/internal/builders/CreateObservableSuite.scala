/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.Cancelable
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.exceptions.DummyException
import scala.util.Success

object CreateObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "Scheduler should be left with no pending tasks")
  }

  test("should work") { implicit s =>
    val o = Observable.create[Int](Unbounded) { out =>
      out.onNext(1)
      out.onNext(2)
      out.onNext(3)
      out.onNext(4)
      out.onComplete()
      Cancelable.empty
    }

    val sum = o.sumT.runAsync
    s.tick()
    assertEquals(sum.value.get, Success(10))
  }

  test("should protect against user error") { implicit s =>
    val ex = DummyException("dummy")
    val o = Observable.create[Int](Unbounded) { out =>
      throw ex
    }

    val sum = o.sumT.runAsync
    s.tick()

    assertEquals(sum.value, None)
    assertEquals(s.state.get.lastReportedError, ex)
  }
}
