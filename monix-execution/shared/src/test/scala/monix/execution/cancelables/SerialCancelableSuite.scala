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

package monix.execution.cancelables

import minitest.SimpleTestSuite

object SerialCancelableSuite extends SimpleTestSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = SerialCancelable(sub)

    assert(effect == 0)
    assert(!sub.isCanceled)
    assert(!mSub.isCanceled)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assert(effect == 1)

    mSub.cancel()
    assert(sub.isCanceled && mSub.isCanceled)
    assert(effect == 1)
  }

  test("cancel() after second assignment") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = SerialCancelable(sub)
    val sub2 = BooleanCancelable(() => effect += 10)
    mSub := sub2

    assert(effect == 1)
    assert(sub.isCanceled && !sub2.isCanceled && !mSub.isCanceled)

    mSub.cancel()
    assert(sub2.isCanceled && mSub.isCanceled && sub.isCanceled)
    assertEquals(effect, 11)
  }

  test("automatically cancel assigned") {
    val mSub = SerialCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)

    assert(effect == 0)
    assert(!sub.isCanceled && mSub.isCanceled)

    mSub := sub
    assert(effect == 1)
    assert(sub.isCanceled)
  }
}
