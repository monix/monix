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

package monix.catnap
package cancelables

import cats.effect.IO
import monix.execution.BaseTestSuite
import monix.execution.exceptions.{ CompositeException, DummyException }

class SingleAssignCancelableFSuite extends BaseTestSuite {
  test("cancel") {
    var effect = 0
    val s = SingleAssignCancelableF[IO].unsafeRunSync()
    val b = BooleanCancelableF.unsafeApply(IO { effect += 1 })

    s.set(b).unsafeRunSync()
    assert(!s.isCanceled.unsafeRunSync(), "!s.isCanceled")

    s.cancel.unsafeRunSync()
    assert(s.isCanceled.unsafeRunSync(), "s.isCanceled")
    assert(b.isCanceled.unsafeRunSync())
    assert(effect == 1)

    s.cancel.unsafeRunSync()
    assert(effect == 1)
  }

  test("cancel (plus one)") {
    var effect = 0
    val extra = BooleanCancelableF.unsafeApply(IO { effect += 1 })
    val b = BooleanCancelableF.unsafeApply(IO { effect += 2 })

    val s = SingleAssignCancelableF.plusOne(extra).unsafeRunSync()
    s.set(b).unsafeRunSync()

    s.cancel.unsafeRunSync()
    assert(s.isCanceled.unsafeRunSync())
    assert(b.isCanceled.unsafeRunSync())
    assert(extra.isCanceled.unsafeRunSync())
    assert(effect == 3)

    s.cancel.unsafeRunSync()
    assert(effect == 3)
  }

  test("cancel on single assignment") {
    val s = SingleAssignCancelableF[IO].unsafeRunSync()
    s.cancel.unsafeRunSync()
    assert(s.isCanceled.unsafeRunSync())

    var effect = 0
    val b = BooleanCancelableF.unsafeApply(IO { effect += 1 })
    s.set(b).unsafeRunSync()

    assert(b.isCanceled.unsafeRunSync())
    assert(effect == 1)

    s.cancel.unsafeRunSync()
    assert(effect == 1)
  }

  test("cancel on single assignment (plus one)") {
    var effect = 0
    val extra = BooleanCancelableF.unsafeApply(IO { effect += 1 })
    val s = SingleAssignCancelableF.plusOne(extra).unsafeRunSync()

    s.cancel.unsafeRunSync()
    assert(s.isCanceled.unsafeRunSync(), "s.isCanceled")
    assert(extra.isCanceled.unsafeRunSync(), "extra.isCanceled")
    assert(effect == 1)

    val b = BooleanCancelableF.unsafeApply(IO { effect += 1 })
    s.set(b).unsafeRunSync()

    assert(b.isCanceled.unsafeRunSync())
    assert(effect == 2)

    s.cancel.unsafeRunSync()
    assert(effect == 2)
  }

  test("throw exception on multi assignment") {
    val s = SingleAssignCancelableF[IO].unsafeRunSync()
    val b1 = CancelableF.empty[IO]
    s.set(b1).unsafeRunSync()

    intercept[IllegalStateException] {
      s.set(CancelableF.empty[IO]).unsafeRunSync()
    }
    ()
  }

  test("throw exception on multi assignment when canceled") {
    val s = SingleAssignCancelableF[IO].unsafeRunSync()
    s.cancel.unsafeRunSync()

    val b1 = CancelableF.empty[IO]
    s.set(b1).unsafeRunSync()

    intercept[IllegalStateException] {
      s.set(CancelableF.empty[IO]).unsafeRunSync()
    }
    ()
  }

  test("cancel when both reference and `extra` throw") {
    var effect = 0
    val dummy1 = DummyException("dummy1")

    val extra = CancelableF.unsafeApply[IO](IO { effect += 1; throw dummy1 })
    val s = SingleAssignCancelableF.plusOne(extra).unsafeRunSync()

    val dummy2 = DummyException("dummy2")
    val b = CancelableF.unsafeApply[IO](IO { effect += 1; throw dummy2 })
    s.set(b).unsafeRunSync()

    try {
      s.cancel.unsafeRunSync()
      fail("should have thrown")
    } catch {
      case CompositeException((_: DummyException) :: (_: DummyException) :: Nil) =>
        ()
      case other: Throwable =>
        throw other
    }
    assertEquals(effect, 2)
  }
}
