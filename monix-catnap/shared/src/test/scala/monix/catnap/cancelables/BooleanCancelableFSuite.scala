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
import minitest.SimpleTestSuite

object BooleanCancelableFSuite extends SimpleTestSuite {
  test("apply") {
    var effect = 0
    val task = IO { effect += 1 }
    val ref = BooleanCancelableF[IO](task)

    val cf = ref.unsafeRunSync()
    assert(!cf.isCanceled.unsafeRunSync(), "!cf.isCanceled")
    assertEquals(effect, 0)
    cf.cancel.unsafeRunSync()
    assert(cf.isCanceled.unsafeRunSync(), "cf.isCanceled")
    assertEquals(effect, 1)
    cf.cancel.unsafeRunSync()
    assert(cf.isCanceled.unsafeRunSync(), "cf.isCanceled")
    assertEquals(effect, 1)

    // Referential transparency test
    val cf2 = ref.unsafeRunSync()
    assert(!cf2.isCanceled.unsafeRunSync(), "!cf2.isCanceled")
    assertEquals(effect, 1)
    cf2.cancel.unsafeRunSync()
    assert(cf2.isCanceled.unsafeRunSync(), "cf2.isCanceled")
    assertEquals(effect, 2)
    cf2.cancel.unsafeRunSync()
    assert(cf2.isCanceled.unsafeRunSync(), "cf2.isCanceled")
    assertEquals(effect, 2)
  }

  test("alreadyCanceled") {
    val cf = BooleanCancelableF.alreadyCanceled[IO]
    assert(cf.isCanceled.unsafeRunSync(), "cf.isCanceled")
    cf.cancel.unsafeRunSync()
    cf.cancel.unsafeRunSync()
    assert(cf.isCanceled.unsafeRunSync(), "cf.isCanceled")
  }

  test("dummy") {
    val cf = BooleanCancelableF.dummy[IO]
    assert(!cf.isCanceled.unsafeRunSync(), "!cf.isCanceled")
    cf.cancel.unsafeRunSync()
    cf.cancel.unsafeRunSync()
    assert(!cf.isCanceled.unsafeRunSync(), "!cf.isCanceled")
  }
}
