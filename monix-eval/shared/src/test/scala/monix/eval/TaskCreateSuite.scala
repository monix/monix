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

import cats.effect.IO
import monix.execution.Cancelable
import monix.execution.exceptions.DummyException

import scala.util.Success
import scala.concurrent.duration._

object TaskCreateSuite extends BaseTestSuite {
  test("can use Unit as return type") { implicit sc =>
    val task = Task.create[Int]((_, cb) => cb.onSuccess(1))
    val f = task.runAsync

    assertEquals(f.value, Some(Success(1)))
  }

  test("can use Cancelable.empty as return type") { implicit sc =>
    val task = Task.create[Int] { (_, cb) => cb.onSuccess(1); Cancelable.empty }
    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("returning Unit yields non-cancelable tasks") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
      ()
    }

    val f = task.continual.runAsync
    sc.tick()
    assertEquals(f.value, None)
    assert(sc.state.tasks.nonEmpty, "tasks.nonEmpty")

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("can use Cancelable as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  test("returning Cancelable yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  test("can use IO[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      IO(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  test("returning IO[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      IO(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  test("can use Task[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Task.evalAsync(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  test("returning Task[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Task.evalAsync(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  test("can use Coeval[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Coeval(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  test("returning Coeval[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Coeval(c.cancel())
    }

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  test("throwing error when returning Unit reports it") { implicit sc =>
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, _) =>
      (throw dummy) : Unit
    }

    val f = task.runAsync
    sc.tick()

    assertEquals(f.value, None)
    assertEquals(sc.state.lastReportedError, dummy)
  }

  test("throwing error when returning Cancelable reports it") { implicit sc =>
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, _) =>
      (throw dummy) : Cancelable
    }

    val f = task.runAsync
    sc.tick()

    assertEquals(f.value, None)
    assertEquals(sc.state.lastReportedError, dummy)
  }
}
