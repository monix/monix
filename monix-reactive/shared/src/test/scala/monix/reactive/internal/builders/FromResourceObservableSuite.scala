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

package monix.reactive.internal.builders

import cats.effect.Resource
import monix.eval.Task
import monix.reactive.{ BaseTestSuite, Observable }

import scala.concurrent.duration._
import scala.util.Success

class FromResourceObservableSuite extends BaseTestSuite {
  class Semaphore(var acquired: Int = 0, var released: Int = 0) {
    def acquire: Task[Handle] =
      Task { acquired += 1 }.map(_ => Handle(this))
  }

  case class Handle(r: Semaphore) {
    def release = Task { r.released += 1 }
  }

  fixture.test("fromResource(Allocate)") { implicit s =>
    val r = new Semaphore
    val res = Resource.make(r.acquire)(_.release)

    val f = Observable
      .fromResource(res)
      .mapEval(_ => Task.now(1).delayExecution(1.second))
      .runAsyncGetFirst

    s.tick()
    assertEquals(r.acquired, 1)
    assertEquals(r.released, 0)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(r.released, 1)
    assertEquals(f.value, Some(Success(Some(1))))
  }

  fixture.test("fromResource(Suspend(FlatMap(Allocate)))") { implicit s =>
    val r = new Semaphore
    val res = Resource.make(r.acquire)(_.release)
    val res2 = Resource.suspend(Task(res.flatMap(_ => res)))

    val f = Observable
      .fromResource(res2)
      .mapEval(_ => Task.now(1).delayExecution(1.second))
      .runAsyncGetFirst

    s.tick()
    assertEquals(r.acquired, 2)
    assertEquals(r.released, 0)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(r.released, 2)
    assertEquals(f.value, Some(Success(Some(1))))
  }
}
