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
import monix.execution.Cancelable

object MultiAssignmentCancelableSuite extends SimpleTestSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = MultiAssignmentCancelable(sub)

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
    val mSub = MultiAssignmentCancelable(sub)
    val sub2 = BooleanCancelable(() => effect += 10)
    mSub := sub2

    assert(effect == 0)
    assert(!sub.isCanceled && !sub2.isCanceled && !mSub.isCanceled)

    mSub.cancel()
    assert(sub2.isCanceled && mSub.isCanceled && !sub.isCanceled)
    assert(effect == 10)
  }

  test("automatically cancel assigned") {
    val mSub = MultiAssignmentCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)

    assert(effect == 0)
    assert(!sub.isCanceled && mSub.isCanceled)

    mSub := sub
    assert(effect == 1)
    assert(sub.isCanceled)
  }

  test("should do orderedUpdate") {
    val mc = MultiAssignmentCancelable()
    var effect = 0

    val c1 = Cancelable { () => effect = 1 }
    mc.orderedUpdate(c1, 1)
    val c2 = Cancelable { () => effect = 2 }
    mc.orderedUpdate(c2, 2)
    val c3 = Cancelable { () => effect = 3 }
    mc.orderedUpdate(c3, 1)

    mc.cancel()
    assertEquals(effect, 2)
  }

  test("orderedUpdate should work on overflow") {
    val mc = MultiAssignmentCancelable()
    var effect = 0

    val c1 = Cancelable { () => effect = 1 }
    mc.orderedUpdate(c1, Long.MaxValue)
    val c2 = Cancelable { () => effect = 2 }
    mc.orderedUpdate(c2, Long.MaxValue+1)
    val c3 = Cancelable { () => effect = 3 }
    mc.orderedUpdate(c3, Long.MaxValue+2)
    val c4 = Cancelable { () => effect = 4 }
    mc.orderedUpdate(c4, Long.MaxValue)

    mc.cancel()
    assertEquals(effect, 3)
  }
}
