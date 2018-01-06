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

package monix.execution.misc

import cats.Eval
import minitest.SimpleTestSuite
import monix.execution.schedulers.{TestScheduler, TracingScheduler}

import scala.concurrent.Future
import scala.util.Success

object LocalSuite extends SimpleTestSuite {
  test("Local.apply") {
    val local = Local(0)
    assertEquals(local.get, 0)

    local := 100
    assertEquals(local.get, 100)

    local.clear()
    assertEquals(local.get, 0)
  }

  test("new Local(lazy)") {
    var i = 0
    val local = new Local({ i += 1; i })
    assertEquals(local.get, 1)

    local := 100
    assertEquals(local.get, 100)

    local.clear()
    assertEquals(local.get, 2)
    assertEquals(local.get, 3)
  }

  test("bind") {
    val local = Local(0)
    local := 100
    assertEquals(local.get, 100)

    val r = local.bind(200) {
      local.get * 2
    }

    assertEquals(r, 400)
    assertEquals(local.get, 100)
  }

  test("bindClear") {
    val local = Local(10)
    local := 100
    assertEquals(local.get, 100)

    val r = local.bindClear(local.get * 2)
    assertEquals(r, 20)
    assertEquals(local.get, 100)
  }

  test("snapshot doesn't get captured in lazy execution") {
    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val value = local1.bind(100)(Eval.always(local1.get + local2.get))
    local1 := 999
    local2 := 999

    assertEquals(value.value, 999 * 2)
  }

  test("captures snapshot in simulated async execution") {
    val ec = TestScheduler()
    implicit val traced = TracingScheduler(ec)

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future(local1.get + local2.get))
    local1 := 999
    local2 := 999

    assertEquals(f.value, None)
    ec.tick()
    assertEquals(f.value, Some(Success(200)))
  }

  testAsync("captures snapshot in actual async execution") {
    import monix.execution.Scheduler.Implicits.traced

    val local1 = Local(0)
    val local2 = Local(0)
    local2 := 100

    val f = local1.bind(100)(Future(local1.get + local2.get))
    local1 := 999
    local2 := 999

    for (r <- f) yield assertEquals(r, 200)
  }

  test("closed") {
    val local = Local(0)
    local := 100

    val f = local.bind(200)(Local.closed(() => local.get))
    assertEquals(local.get, 100)
    assertEquals(f(), 200)
  }

  test("Local.bindClear") {
    val l1 = Local(10)
    val l2 = Local(20)

    l1 := 300
    l2 := 400

    val r = Local.bindClear(l1.get + l2.get)
    assertEquals(l1.get, 300)
    assertEquals(l2.get, 400)
    assertEquals(r, 30)
  }

  test("Local.bind(null) works") {
    val r = Local.bind(null) { 10 + 10 }
    assertEquals(r, 20)
  }
}
