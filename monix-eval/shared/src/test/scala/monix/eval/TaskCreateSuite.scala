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

import cats.effect.IO
import monix.execution.Cancelable
import monix.execution.exceptions.DummyException

import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class TaskCreateSuite extends BaseTestSuite {
  fixture.test("can use Unit as return type") { implicit sc =>
    val task = Task.create[Int]((_, cb) => cb.onSuccess(1))
    val f = task.runToFuture

    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("can use Cancelable.empty as return type") { implicit sc =>
    val task = Task.create[Int] { (_, cb) =>
      cb.onSuccess(1); Cancelable.empty
    }
    val f = task.runToFuture

    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("returning Unit yields non-cancelable tasks") { implicit sc =>
    implicit val opts = Task.defaultOptions.disableAutoCancelableRunLoops

    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
      ()
    }

    val f = task.runToFutureOpt
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("can use Cancelable as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("returning Cancelable yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      sc.scheduleOnce(1.second)(cb.onSuccess(1))
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  fixture.test("can use IO[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      IO(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("returning IO[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      IO(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  fixture.test("can use Task[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Task.evalAsync(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("returning Task[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Task.evalAsync(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  fixture.test("can use Coeval[Unit] as return type") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Coeval(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    sc.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
  }

  fixture.test("returning Coeval[Unit] yields a cancelable task") { implicit sc =>
    val task = Task.create[Int] { (sc, cb) =>
      val c = sc.scheduleOnce(1.second)(cb.onSuccess(1))
      Coeval(c.cancel())
    }

    val f = task.runToFuture
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    sc.tick(1.second)
    assertEquals(f.value, None)
  }

  fixture.test("throwing error when returning Unit") { implicit sc =>
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, _) =>
      (throw dummy): Unit
    }

    val f = task.runToFuture
    sc.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(sc.state.lastReportedError, null)
  }

  fixture.test("throwing error when returning Cancelable") { implicit sc =>
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, _) =>
      (throw dummy): Cancelable
    }

    val f = task.runToFuture
    sc.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(sc.state.lastReportedError, null)
  }
}
