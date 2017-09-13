/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

object ChainedCancelableSuite extends SimpleTestSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = ChainedCancelable(sub)

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
    val mSub = ChainedCancelable(sub)
    val sub2 = BooleanCancelable(() => effect += 10)
    mSub := sub2

    assert(effect == 0)
    assert(!sub.isCanceled && !sub2.isCanceled && !mSub.isCanceled)

    mSub.cancel()
    assert(sub2.isCanceled && mSub.isCanceled && !sub.isCanceled)
    assert(effect == 10)
  }

  test("automatically cancel assigned") {
    val mSub = ChainedCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)

    assert(effect == 0)
    assert(!sub.isCanceled && mSub.isCanceled)

    mSub := sub
    assert(effect == 1)
    assert(sub.isCanceled)
  }

  test(":= throws on null") {
    val c = ChainedCancelable()
    intercept[NullPointerException] { c := null }
  }

  test("chain once") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source = ChainedCancelable()
    val child1 = ChainedCancelable()

    child1.chainTo(source)
    child1 := c1

    assert(!c1.isCanceled, "!c1.isCanceled")

    source.cancel()
    assert(c1.isCanceled, "c1.isCanceled")
    assert(source.isCanceled, "source.isCanceled")
    assert(child1.isCanceled, "child1.isCanceled")

    child1 := c2
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("chain twice") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source = ChainedCancelable()
    val child1 = ChainedCancelable()
    val child2 = ChainedCancelable()

    child1.chainTo(source)
    child2.chainTo(child1)

    child2 := c1
    assert(!c1.isCanceled, "!c1.isCanceled")

    source.cancel()

    assert(c1.isCanceled, "c1.isCanceled")
    assert(source.isCanceled, "source.isCanceled")
    assert(child1.isCanceled, "child1.isCanceled")
    assert(child2.isCanceled, "child2.isCanceled")

    child2 := c2
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("chain after source cancel") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source = ChainedCancelable(c1)
    val child1 = ChainedCancelable()

    source.cancel()
    assert(c1.isCanceled, "c1.isCanceled")

    child1.chainTo(source)
    assert(child1.isCanceled, "child1.isCanceled")

    child1 := c2
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("chain after child cancel") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source1 = ChainedCancelable(c1)
    val source2 = ChainedCancelable()
    val child = ChainedCancelable()

    child.cancel()
    assert(!c1.isCanceled, "!c1.isCanceled")

    child.chainTo(source1)
    assert(child.isCanceled, "child1.isCanceled")
    assert(source1.isCanceled, "source.isCanceled")
    assert(c1.isCanceled, "c1.isCanceled")

    child := c2
    assert(c2.isCanceled, "c2.isCanceled")
    assert(child.isCanceled, "child.isCanceled")

    child.chainTo(source2)
    assert(child.isCanceled, "child.isCanceled")
    assert(source2.isCanceled, "source2.isCanceled")
  }
}
