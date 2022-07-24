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

import cats.effect.IO
import monix.execution.BaseTestSuite

class CancelableFSuite extends BaseTestSuite {
  test("apply") {
    var effect = 0
    val task = IO { effect += 1 }
    val ref = CancelableF[IO](task)

    val cf = ref.unsafeRunSync()
    assertEquals(effect, 0)
    cf.cancel.unsafeRunSync()
    assertEquals(effect, 1)
    cf.cancel.unsafeRunSync()
    assertEquals(effect, 1)

    val cf2 = ref.unsafeRunSync()
    assertEquals(effect, 1)
    cf2.cancel.unsafeRunSync()
    assertEquals(effect, 2)
    cf2.cancel.unsafeRunSync()
    assertEquals(effect, 2)
  }

  test("empty") {
    val cf = CancelableF.empty[IO]
    cf.cancel.unsafeRunSync()
    cf.cancel.unsafeRunSync()
  }

  test("wrap is not idempotent") {
    var effect = 0
    val token = CancelableF.wrap(IO { effect += 1 })
    token.cancel.unsafeRunSync()
    token.cancel.unsafeRunSync()
    token.cancel.unsafeRunSync()
    assertEquals(effect, 3)
  }

  test("cancel multiple cancelables") {
    var effect = 0
    val seq = (0 until 100).map(_ => CancelableF.unsafeApply(IO { effect += 1 }))
    val col = CancelableF.collection(seq: _*)

    assertEquals(effect, 0)
    col.cancel.unsafeRunSync()
    assertEquals(effect, 100)
  }

  test("cancel multiple tokens") {
    var effect = 0
    val seq = (0 until 100).map(_ => IO { effect += 1 })
    val cancel = CancelableF.cancelAllTokens(seq: _*)

    assertEquals(effect, 0)
    cancel.unsafeRunSync()
    assertEquals(effect, 100)
    cancel.unsafeRunSync()
    assertEquals(effect, 200)
  }

}
