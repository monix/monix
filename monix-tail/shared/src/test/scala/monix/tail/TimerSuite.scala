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

package monix.tail

import cats.effect.IO
import monix.eval.Task
import monix.tail.util.Timer

import scala.concurrent.duration._
import scala.util.Success

object TimerSuite extends BaseTestSuite {
  test("Timer[Task].currentTimeMillis") { implicit sc =>
    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 1000)

    val f = Timer[Task].currentTimeMillis.runAsync
    assertEquals(f.value, Some(Success(1000)))

    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 2000)

    val f2 = Timer[Task].currentTimeMillis.runAsync
    assertEquals(f2.value, Some(Success(2000)))
  }

  test("Timer[IO].currentTimeMillis") { implicit sc =>
    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 1000)

    val f = Timer[IO].currentTimeMillis.unsafeToFuture()
    assertEquals(f.value, Some(Success(1000)))

    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 2000)

    val f2 = Timer[IO].currentTimeMillis.unsafeToFuture()
    assertEquals(f2.value, Some(Success(2000)))
  }

  test("Timer[Task].shift") { implicit sc =>
    val f = Timer[Task].shift.runAsync
    assertEquals(f.value, None)

    sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("Timer[IO].shift") { implicit sc =>
    val f = Timer[IO].shift.unsafeToFuture()
    assertEquals(f.value, None)

    sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("Timer[Task].sleep") { implicit sc =>
    val f = Timer[Task].sleep(10.seconds).runAsync

    for (_ <- 0 until 9) {
      sc.tick(1.second)
      assertEquals(f.value, None)
    }

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(())))
  }

  test("Timer[IO].sleep") { implicit sc =>
    val f = Timer[IO].sleep(10.seconds).unsafeToFuture()

    for (_ <- 0 until 9) {
      sc.tick(1.second)
      assertEquals(f.value, None)
    }

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(())))
  }

  test("Timer[Task].suspendTimed") { implicit sc =>
    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 1000)

    val f = Timer[Task].suspendTimed(Task.now).runAsync
    assertEquals(f.value, Some(Success(1000)))

    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 2000)

    val f2 = Timer[Task].suspendTimed(Task.now).runAsync
    assertEquals(f2.value, Some(Success(2000)))
  }

  test("Timer[IO].suspendTimed") { implicit sc =>
    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 1000)

    val f = Timer[IO].suspendTimed(IO.pure).unsafeToFuture
    assertEquals(f.value, Some(Success(1000)))

    sc.tick(1.second)
    assertEquals(sc.currentTimeMillis(), 2000)

    val f2 = Timer[IO].suspendTimed(IO.pure).unsafeToFuture
    assertEquals(f2.value, Some(Success(2000)))
  }
}
