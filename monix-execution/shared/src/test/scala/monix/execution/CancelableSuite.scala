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

package monix.execution

import cats.effect.IO
import minitest.SimpleTestSuite
import monix.execution.exceptions.{CompositeException, DummyException}
import monix.execution.schedulers.TestScheduler
import scala.concurrent.Promise
import scala.util.Failure

object CancelableSuite extends SimpleTestSuite {
  test("Cancelable.empty") {
    val c = Cancelable()
    assertEquals(c, Cancelable.empty)
    assert(c.toString.toLowerCase.contains("empty"))
  }

  test("Cancelable(task) should be idempotent") {
    var effect = 0
    val c = Cancelable(() => effect += 1)

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 1)
    c.cancel()
    assertEquals(effect, 1)
  }

  test("Cancelable.collection") {
    var effect = 0
    val c = Cancelable.collection((0 until 100).map(_ => Cancelable(() => effect += 1)))

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 100)
  }

  test("Cancelable.collection") {
    var effect = 0
    val dummy = DummyException("dummy")

    val c = Cancelable.collection((0 until 100).map(_ => Cancelable { () =>
      effect += 1
      throw dummy
    }))

    try {
      c.cancel()
      fail("c.cancel() should throw")
    } catch {
      case e: CompositeException =>
        assertEquals(e.errors.toList, (0 until 100).map(_ => dummy))
    }
  }

  test("Cancelable.fromPromise") {
    val p = Promise[Unit]()
    val dummy = DummyException("dummy")

    val c = Cancelable.fromPromise(p, dummy)
    assertEquals(p.future.value, None)

    c.cancel()
    assertEquals(p.future.value, Some(Failure(dummy)))
  }

  test("Cancelable.fromIO") {
    implicit val ctx = TestScheduler()

    var effect = 0
    val io = IO {
      effect += 1
    }
    val c = Cancelable.fromIO(io)

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 1)
    c.cancel()
    assertEquals(effect, 1)
  }

  test("Cancelable.fromIO reports error") {
    implicit val ctx = TestScheduler()
    val dummy = DummyException("dummy")

    val io = IO {
      throw dummy
    }
    val c = Cancelable.fromIO(io)

    assertEquals(ctx.state.lastReportedError, null)
    c.cancel()
    assertEquals(ctx.state.lastReportedError, dummy)
  }

  test("Cancelable#cancelIO") {
    var effect = 0
    val c = Cancelable { () => effect += 1 }
    val io = c.cancelIO

    assertEquals(effect, 0)
    io.unsafeRunSync()
    assertEquals(effect, 1)
    io.unsafeRunSync()
    assertEquals(effect, 1)
  }

  test("Cancelable.empty.cancelIO == IO.unit") {
    assertEquals(Cancelable.empty.cancelIO, IO.unit)
  }
}
