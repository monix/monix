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

import scala.util.Success

class TaskExecuteWithOptionsSuite extends BaseTestSuite {
  fixture.test("executeWithOptions works") { implicit s =>
    val task = Task
      .eval(1)
      .flatMap(_ => Task.eval(2))
      .flatMap(_ => Task.readOptions)
      .executeWithOptions(_.enableLocalContextPropagation)
      .flatMap(opt1 => Task.readOptions.map(opt2 => (opt1, opt2)))

    val f = task.runToFuture
    s.tick()

    val Some(Success((opt1, opt2))) = f.value
    assert(opt1.localContextPropagation, "opt1.localContextPropagation")
    assert(!opt2.localContextPropagation, "!opt2.localContextPropagation")
  }

  test("local.write.executeWithOptions") {
    import monix.execution.Scheduler.Implicits.global

    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeWithOptions(_.enableAutoCancelableRunLoops)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }

  fixture.test("executeWithOptions is stack safe in flatMap loops") { implicit sc =>
    def loop(n: Int, acc: Long): Task[Long] =
      Task.unit.executeWithOptions(_.enableAutoCancelableRunLoops).flatMap { _ =>
        if (n > 0)
          loop(n - 1, acc + 1)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000L)))
  }
}
