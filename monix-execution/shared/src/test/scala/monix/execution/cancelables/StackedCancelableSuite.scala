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
    c push initial
    c.cancel()
    assertEquals(effect, 1)
  }

  test("cancels after being canceled") {
    var effect = 0
    val initial = Cancelable(() => effect += 1)
    val c = StackedCancelable()
    c.cancel()
    c push initial
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
    c2 push d4

    assertEquals(c2.popAndPushList(List(d1,d2)), d4)
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
}
