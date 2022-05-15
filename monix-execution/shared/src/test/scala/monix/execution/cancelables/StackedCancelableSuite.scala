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

import minitest.SimpleTestSuite
import monix.execution.Cancelable

object StackedCancelableSuite extends SimpleTestSuite {
  test("cancels initial") {
    var effect = 0
    val initial = Cancelable(() => effect += 1)
    val c = StackedCancelable(initial)
    c.cancel()
    assertEquals(effect, 1)
  }

  test("initial push") {
    var effect = 0
    val initial = Cancelable(() => effect += 1)
    val c = StackedCancelable()
    c.push(initial)
    c.cancel()
    assertEquals(effect, 1)
  }

  test("cancels after being canceled") {
    var effect = 0
    val initial = Cancelable(() => effect += 1)
    val c = StackedCancelable()
    c.cancel()
    c.push(initial)
    assertEquals(effect, 1)
  }

  test("push two, pop one") {
    var effect = 0
    val initial1 = Cancelable(() => effect += 1)
    val initial2 = Cancelable(() => effect += 2)

    val c = StackedCancelable()
    c.push(initial1)
    c.push(initial2)
    c.pop()
    c.cancel()

    assertEquals(effect, 1)
  }

  test("cancel the second time is a no-op") {
    val bc = BooleanCancelable()
    val c = StackedCancelable(bc)

    c.cancel()
    assert(bc.isCanceled, "bc.isCanceled")
    c.cancel()
    assert(bc.isCanceled, "bc.isCanceled")
  }

  test("push two, pop two") {
    var effect = 0
    val initial1 = Cancelable(() => effect += 1)
    val initial2 = Cancelable(() => effect += 2)

    val c = StackedCancelable()
    c.push(initial1)
    c.push(initial2)
    assertEquals(c.pop(), initial2)
    assertEquals(c.pop(), initial1)
    c.cancel()

    assertEquals(effect, 0)
  }

  test("pop and push") {
    var effect = 0
    val initial1 = Cancelable(() => effect += 1)
    val initial2 = Cancelable(() => effect += 2)
    val initial3 = Cancelable(() => effect += 3)

    val c = StackedCancelable()
    c.push(initial1)
    c.push(initial2)

    assertEquals(c.popAndPush(initial3), initial2)
    c.cancel()
    assertEquals(effect, 4)
  }

  test("pop and push when self is canceled") {
    val sc = StackedCancelable()
    sc.cancel()

    val c = BooleanCancelable()
    val r = sc.popAndPush(c)

    assertEquals(r, Cancelable.empty)
    assert(c.isCanceled, "c.isCanceled")
  }

  test("pop and push when self is empty") {
    val sc = StackedCancelable()
    val c = BooleanCancelable()
    val r = sc.popAndPush(c)

    assertEquals(r, Cancelable.empty)
    assert(!c.isCanceled, "!c.isCanceled")

    sc.cancel()
    assert(c.isCanceled, "c.isCanceled")
    assert(sc.isCanceled, "sc.isCanceled")
  }

  test("pop when self is empty") {
    val sc = StackedCancelable()
    assertEquals(sc.pop(), Cancelable.empty)
  }

  test("pop when self is canceled") {
    val sc = StackedCancelable()
    sc.cancel()
    assertEquals(sc.pop(), Cancelable.empty)
  }

  test("popAndPushList") {
    var effect = 0

    val d1 = Cancelable(() => effect += 1)
    val d2 = Cancelable(() => effect += 2)
    val d3 = Cancelable(() => effect += 10)
    val d4 = Cancelable(() => effect += 20)

    val c2 = StackedCancelable(d3)
    c2.push(d4)

    assertEquals(c2.popAndPushList(List(d1, d2)), d4)
    c2.cancel()
    assertEquals(effect, 13)
  }

  test("popAndPushList when self is empty") {
    val sc = StackedCancelable()
    val c = BooleanCancelable()

    sc.popAndPushList(List(c))
    assert(!sc.isCanceled, "!sc.isCanceled")
    assert(!c.isCanceled, "!c.isCanceled")

    sc.cancel()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("popAndPushAll when self is canceled") {
    val sc = StackedCancelable()
    sc.cancel()

    val c = BooleanCancelable()

    sc.popAndPushList(List(c))
    assert(sc.isCanceled, "sc.isCanceled")
    assert(c.isCanceled, "c.isCanceled")
  }

  test("alreadyCanceled returns same reference") {
    val ref1 = StackedCancelable.alreadyCanceled
    val ref2 = StackedCancelable.alreadyCanceled
    assertEquals(ref1, ref2)
  }

  test("alreadyCanceled reference is already cancelled") {
    val ref = StackedCancelable.alreadyCanceled
    assert(ref.isCanceled, "ref.isCanceled")
    ref.cancel()
    assert(ref.isCanceled, "ref.isCanceled")
  }

  test("alreadyCanceled.pop") {
    val ref = StackedCancelable.alreadyCanceled
    assertEquals(ref.pop(), Cancelable.empty)
  }

  test("alreadyCanceled.push cancels the given cancelable") {
    val ref = StackedCancelable.alreadyCanceled
    val c = BooleanCancelable()
    ref.push(c)

    assert(c.isCanceled, "c.isCanceled")
  }

  test("alreadyCanceled.popAndPush cancels the given cancelable") {
    val ref = StackedCancelable.alreadyCanceled
    val c = BooleanCancelable()

    assertEquals(ref.popAndPush(c), Cancelable.empty)
    assert(c.isCanceled, "c.isCanceled")
  }

  test("alreadyCanceled.pushList cancels the given list") {
    val ref = StackedCancelable.alreadyCanceled
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()
    ref.pushList(List(c1, c2))

    assert(c1.isCanceled, "c1.isCanceled")
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("alreadyCanceled.popAndPushList cancels the given list") {
    val ref = StackedCancelable.alreadyCanceled
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    assertEquals(ref.popAndPushList(List(c1, c2)), Cancelable.empty)
    assert(c1.isCanceled, "c1.isCanceled")
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("pushList") {
    val ref = StackedCancelable()

    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()
    val c3 = BooleanCancelable()
    val c4 = BooleanCancelable()

    ref.push(c1)
    ref.pushList(List(c2, c3, c4))

    assertEquals(ref.pop(), c4)
    ref.cancel()

    assert(c1.isCanceled, "c1.isCanceled")
    assert(c2.isCanceled, "c2.isCanceled")
    assert(c3.isCanceled, "c3.isCanceled")
    assert(!c4.isCanceled, "!c4.isCanceled")
  }

  test("pushList after cancel") {
    val ref = StackedCancelable()
    ref.cancel()

    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()
    val c3 = BooleanCancelable()
    val c4 = BooleanCancelable()

    ref.pushList(List(c1, c2, c3, c4))

    assert(c1.isCanceled, "c1.isCanceled")
    assert(c2.isCanceled, "c2.isCanceled")
    assert(c3.isCanceled, "c3.isCanceled")
    assert(c4.isCanceled, "c4.isCanceled")
  }

  test("uncanceled returns same reference") {
    val ref1 = StackedCancelable.uncancelable
    val ref2 = StackedCancelable.uncancelable
    assertEquals(ref1, ref2)
  }

  test("uncancelable reference cannot be seen in cancelled state") {
    val ref = StackedCancelable.uncancelable
    assert(!ref.isCanceled, "!ref.isCanceled")
    ref.cancel()
    assert(!ref.isCanceled, "!ref.isCanceled")
  }

  test("uncancelable.pop") {
    val ref = StackedCancelable.uncancelable
    assertEquals(ref.pop(), Cancelable.empty)
  }

  test("uncancelable.push does not cancel the given cancelable") {
    val ref = StackedCancelable.uncancelable
    val c = BooleanCancelable()
    ref.push(c)
    assert(!c.isCanceled, "!c.isCanceled")
  }

  test("uncancelable.popAndPush does not cancel the given cancelable") {
    val ref = StackedCancelable.uncancelable
    val c = BooleanCancelable()

    assertEquals(ref.popAndPush(c), Cancelable.empty)
    assert(!c.isCanceled, "!c.isCanceled")
  }

  test("uncancelable.pushList cancels the given list") {
    val ref = StackedCancelable.uncancelable
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()
    ref.pushList(List(c1, c2))

    assert(!c1.isCanceled, "!c1.isCanceled")
    assert(!c2.isCanceled, "!c2.isCanceled")
  }

  test("uncancelable.popAndPushList cancels the given list") {
    val ref = StackedCancelable.uncancelable
    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()

    assertEquals(ref.popAndPushList(List(c1, c2)), Cancelable.empty)
    assert(!c1.isCanceled, "!c1.isCanceled")
    assert(!c2.isCanceled, "!c2.isCanceled")
  }
}
