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

import monix.execution.schedulers.TestScheduler
import scala.util.Success

object TaskExecuteOnSuite extends BaseTestSuite {

  test("executeOn(forceAsync = false)") { implicit sc =>
    val sc2 = TestScheduler()
    val fa = Task.eval(1).executeOn(sc2, forceAsync = false)
    val f = fa.runToFuture

    assertEquals(f.value, Some(Success(1)))
  }

  test("executeOn(forceAsync = true)") { implicit sc =>
    val sc2 = TestScheduler()
    val fa = Task.eval(1).executeOn(sc2)
    val f = fa.runToFuture

    assertEquals(f.value, None)
    sc.tick()
    assertEquals(f.value, None)
    sc2.tick()
    assertEquals(f.value, None)
    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("executeOn(forceAsync = false) is stack safe in flatMap loops, test 1") { implicit sc =>
    val sc2 = TestScheduler()

    def loop(n: Int, acc: Long): Task[Long] =
      Task.unit.executeOn(sc2, forceAsync = false).flatMap { _ =>
        if (n > 0)
          loop(n - 1, acc + 1)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("executeOn(forceAsync = false) is stack safe in flatMap loops, test 2") { implicit sc =>
    val sc2 = TestScheduler()

    def loop(n: Int, acc: Long): Task[Long] =
      Task.unit.flatMap { _ =>
        if (n > 0)
          loop(n - 1, acc + 1).executeOn(sc2, forceAsync = false)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture
    sc.tick()
    sc2.tick()

    assertEquals(f.value, Some(Success(10000)))
  }

  testAsync("local.write.executeOn(forceAsync = false) works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeOn(global, forceAsync = false)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }

  testAsync("local.write.executeOn(forceAsync = true) works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeOn(global)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }
}
