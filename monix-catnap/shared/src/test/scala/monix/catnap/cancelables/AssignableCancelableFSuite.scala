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

package monix.catnap.cancelables

import cats.effect.IO
import monix.execution.BaseTestSuite
import monix.catnap.CancelableF

class AssignableCancelableFSuite extends BaseTestSuite {
  test("alreadyCanceled") {
    val ac = AssignableCancelableF.alreadyCanceled[IO]
    var effect = 0

    assertEquals(ac.isCanceled.unsafeRunSync(), true)
    ac.set(CancelableF.wrap(IO { effect += 1 })).unsafeRunSync()
    assertEquals(effect, 1)

    ac.cancel.unsafeRunSync()
    assertEquals(effect, 1)
    ac.set(CancelableF.wrap(IO { effect += 1 })).unsafeRunSync()
    assertEquals(effect, 2)
  }

  test("dummy") {
    val ac = AssignableCancelableF.dummy[IO]
    var effect = 0

    assertEquals(ac.isCanceled.unsafeRunSync(), false)
    ac.set(CancelableF.wrap(IO { effect += 1 })).unsafeRunSync()
    assertEquals(effect, 0)

    ac.cancel.unsafeRunSync()
    assertEquals(effect, 0)
    ac.set(CancelableF.wrap(IO { effect += 1 })).unsafeRunSync()
    assertEquals(effect, 0)

    assertEquals(ac.isCanceled.unsafeRunSync(), false)
  }
}
