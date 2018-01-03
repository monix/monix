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

object AssignableCancelableSuite extends SimpleTestSuite {
  test("AssignableCancelable.multi() returns a MultiAssignmentCancelable") {
    val c = AssignableCancelable.multi()
    assert(c.isInstanceOf[MultiAssignmentCancelable],
      "isInstanceOf[MultiAssignmentCancelable]")
  }

  test("AssignableCancelable.single() returns a SingleAssignmentCancelable") {
    val c = AssignableCancelable.single()
    assert(c.isInstanceOf[SingleAssignmentCancelable],
      "isInstanceOf[SingleAssignmentCancelable]")
  }

  test("AssignableCancelable.alreadyCanceled") {
    val c = AssignableCancelable.alreadyCanceled
    assert(c.isCanceled, "c.isCanceled")

    val b = BooleanCancelable(); c := b
    assert(b.isCanceled, "b.isCanceled")

    c.cancel()
    assert(c.isCanceled, "c.isCanceled")
    val b2 = BooleanCancelable(); c := b2
    assert(b2.isCanceled, "b2.isCanceled")
  }

  test("AssignableCancelable.dummy") {
    val c = AssignableCancelable.dummy

    val b = BooleanCancelable(); c := b
    assert(!b.isCanceled, "!b.isCanceled")

    c.cancel()
    val b2 = BooleanCancelable(); c := b2
    assert(!b2.isCanceled, "!b2.isCanceled")
  }
}
