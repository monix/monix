/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
 *
 */

package monix.execution.cancelables

import minitest.SimpleTestSuite
import monix.execution.Cancelable

object SingleAssignmentCancelableSuite extends SimpleTestSuite {
  test("cancel()") {
    var effect = 0
    val s = SingleAssignmentCancelable()
    val b = BooleanCancelable { effect += 1 }
    s := b

    s.cancel()
    assert(s.isCanceled)
    assert(b.isCanceled)
    assert(effect == 1)

    s.cancel()
    assert(effect == 1)
  }

  test("cancel on single assignment") {
    val s = SingleAssignmentCancelable()
    s.cancel()
    assert(s.isCanceled)

    var effect = 0
    val b = BooleanCancelable { effect += 1 }
    s := b

    assert(b.isCanceled)
    assert(effect == 1)

    s.cancel()
    assert(effect == 1)
  }

  test("throw exception on multi assignment") {
    val s = SingleAssignmentCancelable()
    val b1 = Cancelable()
    s := b1

    intercept[IllegalStateException] {
      val b2 = Cancelable()
      s := b2
    }
  }

  test("throw exception on multi assignment when canceled") {
    val s = SingleAssignmentCancelable()
    s.cancel()

    val b1 = Cancelable()
    s := b1

    intercept[IllegalStateException] {
      val b2 = Cancelable()
      s := b2
    }
  }
}
