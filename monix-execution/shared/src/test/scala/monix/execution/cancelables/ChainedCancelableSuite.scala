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

object ChainedCancelableSuite extends SimpleTestSuite {
  test("cancel()") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = ChainedCancelable(sub)

    assert(effect == 0)
    assert(!sub.isCanceled)

    mSub.cancel()
    assert(sub.isCanceled, "sub.isCanceled")
    assert(effect == 1)

    mSub.cancel()
    assert(sub.isCanceled, "sub.isCanceled")
    assert(effect == 1)
  }

  test("cancel() after second assignment") {
    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)
    val mSub = ChainedCancelable(sub)
    val sub2 = BooleanCancelable(() => effect += 10)
    mSub := sub2

    assert(effect == 0)
    assert(!sub.isCanceled && !sub2.isCanceled, "!sub.isCanceled && !sub2.isCanceled")

    mSub.cancel()
    assert(sub2.isCanceled && !sub.isCanceled)
    assert(effect == 10)
  }

  test("automatically cancel assigned") {
    val mSub = ChainedCancelable()
    mSub.cancel()

    var effect = 0
    val sub = BooleanCancelable(() => effect += 1)

    assert(effect == 0)
    assert(!sub.isCanceled, "!sub.isCanceled")

    mSub := sub
    assert(effect == 1)
    assert(sub.isCanceled)
  }

  test(":= does not throw on null") {
    val c = ChainedCancelable()
    c := null
    c := Cancelable.empty
    c := null
    c.cancel()
  }

  test("chain once") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source = ChainedCancelable()
    val child1 = ChainedCancelable()

    child1.forwardTo(source)
    child1 := c1
    assert(!c1.isCanceled, "!c1.isCanceled")

    source.cancel()
    assert(c1.isCanceled, "c1.isCanceled")

    child1 := c2
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("chain twice") {
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    val source = ChainedCancelable()
    val child1 = ChainedCancelable()
    val child2 = ChainedCancelable()

    child1.forwardTo(source)
    child2.forwardTo(child1)

    child2 := c1
    assert(!c1.isCanceled, "!c1.isCanceled")

    source.cancel()
    assert(c1.isCanceled, "c1.isCanceled")

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

    child1.forwardTo(source)

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

    child.forwardTo(source1)
    assert(c1.isCanceled, "c1.isCanceled")

    child := c2
    assert(c2.isCanceled, "c2.isCanceled")

    child.forwardTo(source2)
  }

  test("chained twice, test 1") {
    val cc1 = ChainedCancelable()
    val cc2 = ChainedCancelable()

    val one = ChainedCancelable()
    one.forwardTo(cc1)
    one.forwardTo(cc2)

    val c = BooleanCancelable()
    one := c

    assert(!c.isCanceled, "!c.isCanceled")
    cc1.cancel()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("chained twice, test 2") {
    val cc1 = ChainedCancelable()
    val cc2 = ChainedCancelable()

    val one = ChainedCancelable()
    one.forwardTo(cc1)
    one.forwardTo(cc2)

    val c = BooleanCancelable()
    one := c

    assert(!c.isCanceled, "!c.isCanceled")
    cc2.cancel()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("self.forwardTo(self)") {
    val cc = ChainedCancelable()
    cc.forwardTo(cc)

    val c = BooleanCancelable()
    cc := c

    assert(!c.isCanceled)
    cc.cancel()
    assert(c.isCanceled)
  }

  test("self.forwardTo(other.forwardTo(self))") {
    val cc1 = ChainedCancelable()
    val cc2 = ChainedCancelable()
    val cc3 = ChainedCancelable()

    cc1.forwardTo(cc2)
    cc2.forwardTo(cc3)
    cc3.forwardTo(cc1)

    val c = BooleanCancelable()
    cc3 := c

    assert(!c.isCanceled)
    cc1.cancel()
    assert(c.isCanceled)
  }
}
