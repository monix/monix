/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import minitest.SimpleTestSuite
import monix.execution.exceptions.{ CompositeException, DummyException }
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.control.NonFatal

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

  test("Cancelable.collection(seq)") {
    var effect = 0
    val c = Cancelable.collection((0 until 100).map(_ => Cancelable(() => effect += 1)))

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 100)
  }

  test("Cancelable.collection(refs)") {
    var effect = 0
    val c = Cancelable.collection((0 until 100).map(_ => Cancelable(() => effect += 1)): _*)

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 100)
  }

  test("Cancelable.collection should cancel all on error") {
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val c = Cancelable.collection(
      Cancelable(() => throw dummy1),
      Cancelable(() => throw dummy2)
    )

    try {
      c.cancel()
      fail("c.cancel() should throw")
    } catch {
      case NonFatal(e) =>
        if (Platform.isJVM) {
          assertEquals(e, dummy1)
          assertEquals(e.getSuppressed.toList, List(dummy2))
        } else {
          val CompositeException(errors) = e
          assertEquals(errors.toList, List(dummy1, dummy2))
        }
    }
  }

  test("Cancelable.trampolined(seq)") {
    implicit val sc = TestScheduler()
    var effect = 0
    val c = Cancelable.trampolined((0 until 100).map(_ => Cancelable(() => effect += 1)))

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 100)
  }

  test("Cancelable.trampolined(refs)") {
    implicit val sc = TestScheduler()
    var effect = 0
    val c = Cancelable.trampolined((0 until 100).map(_ => Cancelable(() => effect += 1)): _*)

    assertEquals(effect, 0)
    c.cancel()
    assertEquals(effect, 100)
  }

  test("Cancelable.trampolined should cancel all on error") {
    implicit val sc = TestScheduler()
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val c = Cancelable.trampolined(
      Cancelable(() => throw dummy1),
      Cancelable(() => throw dummy2)
    )

    c.cancel()
    if (Platform.isJVM) {
      val e = sc.state.lastReportedError
      assertEquals(e, dummy1)
      assertEquals(e.getSuppressed.toList, List(dummy2))
    } else {
      val CompositeException(errors) = sc.state.lastReportedError
      assertEquals(errors.toList, List(dummy1, dummy2))
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
}
