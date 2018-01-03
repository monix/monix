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

package monix.execution.rstreams

import minitest.SimpleTestSuite

object SingleAssignmentSubscriptionSuite extends SimpleTestSuite {
  test("should call cancel on assignment") {
    val ref = SingleAssignmentSubscription()
    ref.cancel()

    var wasCanceled = false
    ref := new Subscription {
      def request(n: Long) = ()
      def cancel() = wasCanceled = true
    }

    assert(wasCanceled, "wasCanceled should be true")
  }

  test("should call request on assignment") {
    val ref = SingleAssignmentSubscription()
    ref.request(100)
    ref.request(200)

    var wasRequested = 0L
    ref := new Subscription {
      def request(n: Long) = wasRequested = n
      def cancel() = ()
    }

    assertEquals(wasRequested, 300)
  }

  test("cancel should have priority on assignment") {
    val ref = SingleAssignmentSubscription()
    ref.request(100)
    ref.cancel()
    ref.request(200)

    var wasCanceled = false
    var wasRequested = 0L

    ref := new Subscription {
      def request(n: Long) = wasRequested += n
      def cancel() = wasCanceled = true
    }

    assert(wasCanceled, "wasCanceled should be true")
    assertEquals(wasRequested, 0)
  }

  test("request and cancel after assignment should work") {
    val ref = SingleAssignmentSubscription()
    var wasCanceled = false
    var wasRequested = 0L

    ref := new Subscription {
      def request(n: Long) = wasRequested += n
      def cancel() = wasCanceled = true
    }

    ref.request(100)
    ref.request(200)
    ref.cancel()

    assert(wasCanceled, "wasCanceled should be true")
    assertEquals(wasRequested, 300)
  }
}
