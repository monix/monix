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

import scala.concurrent.duration._
import scala.util.Success

object TaskCancellationSuite extends BaseTestSuite {
  test("cancellation works for async actions") { implicit ec =>
    var wasCancelled = false
    val task = Task.eval(1).delayExecution(1.second)
      .doOnCancel(Task.eval { wasCancelled = true })
      .cancel

    task.runAsync
    assert(wasCancelled, "wasCancelled")
    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("cancellation works for autoCancelableRunLoops") { implicit ec =>
    implicit val opts = Task.defaultOptions.enableAutoCancelableRunLoops

    var effect = 0
    val task = Task(1).flatMap(x => Task(2).map(_ + x))
      .foreachL { x => effect = x }
      .cancel

    val f = task.runAsyncOpt
    ec.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(effect, 0)
  }

  test("uncancelable works for async actions") { implicit ec =>
    var effect = 0
    val task = Task.eval(1).delayExecution(1.second)
      .foreachL { x => effect += x }

    val f = task.uncancelable.flatMap(_ => task).runAsync
    ec.tick()
    assertEquals(effect, 0)

    f.cancel()
    ec.tick(1.second)
    assertEquals(effect, 1)

    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
    ec.tick(1.second)
    assertEquals(f.value, None)
  }

  test("uncancelable works for autoCancelableRunLoops") { implicit ec =>
    implicit val opts = Task.defaultOptions.enableAutoCancelableRunLoops
    val task = Task(1)
    val f = task.flatMap(x => task.map(_ + x)).uncancelable.runAsyncOpt

    f.cancel()
    ec.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("uncancelable is stack safe in flatMap loop, take 1") { implicit ec =>
    def loop(n: Int): Task[Int] =
      Task.eval(n).flatMap { x =>
        if (x > 0)
          Task.eval(x - 1).uncancelable.flatMap(loop)
        else
          Task.pure(0)
      }

    val f = loop(10000).runAsync
    ec.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("uncancelable is stack safe in flatMap loop, take 2") { implicit ec =>
    var task = Task(1)
    for (_ <- 0 until 10000) task = task.uncancelable

    val f = task.runAsync
    ec.tick()
    assertEquals(f.value, Some(Success(1)))
  }
}
