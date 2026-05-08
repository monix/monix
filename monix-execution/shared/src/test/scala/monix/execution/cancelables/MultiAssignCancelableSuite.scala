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

package monix.execution.cancelables

import monix.execution.MUnitFunSuite

class MultiAssignCancelableSuite extends MUnitFunSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = MultiAssignCancelable(sub)

    assertEquals(effect, 0)
    assert(!sub.isCanceled)
    assert(!mSub.isCanceled)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assertEquals(effect, 1)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assertEquals(effect, 1)
  }

  test("cancel() after second assignment") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = MultiAssignCancelable(sub)
    val sub2 = BooleanCancelable(() => effect += 10)
    mSub := sub2

    assertEquals(effect, 0)
    assert(!sub.isCanceled && !sub2.isCanceled && !mSub.isCanceled)

    mSub.cancel()
    assert(sub2.isCanceled && mSub.isCanceled && !sub.isCanceled)
    assertEquals(effect, 10)
  }

  test("automatically cancel assigned") {
    val mSub = MultiAssignCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)

    assertEquals(effect, 0)
    assert(!sub.isCanceled && mSub.isCanceled)

    mSub := sub
    assertEquals(effect, 1)
    assert(sub.isCanceled)
  }

  test("clear()") {
    val mSub = MultiAssignCancelable()
    val sub = BooleanCancelable()

    mSub := sub
    val _ = mSub.clear()

    mSub.cancel()
    assert(!sub.isCanceled, "!sub.isCanceled")

    val _ = mSub.clear()
    mSub := sub
    assert(sub.isCanceled, "sub.isCanceled")
  }
}
