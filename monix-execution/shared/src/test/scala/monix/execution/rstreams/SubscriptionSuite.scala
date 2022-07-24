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

package monix.execution.rstreams

import monix.execution.BaseTestSuite
import org.reactivestreams.{ Subscription => RSubscription }

class SubscriptionSuite extends BaseTestSuite {
  test("wraps any subscription reference") {
    var cancelCalled: Int = 0
    var requestCalled: Long = 0

    val sub = Subscription(new RSubscription {
      def cancel(): Unit =
        cancelCalled += 1
      def request(n: Long): Unit =
        requestCalled += n
    })

    assertEquals(requestCalled, 0L)
    assertEquals(cancelCalled, 0)

    sub.request(3)
    sub.request(7)
    assertEquals(requestCalled, 10L)
    assertEquals(cancelCalled, 0)

    sub.cancel()
    assertEquals(requestCalled, 10L)
    assertEquals(cancelCalled, 1)
  }

  test("Subscription.empty is a no-op") {
    val sub = Subscription.empty
    assertEquals(sub, Subscription.empty)

    sub.request(-100)
    sub.cancel()
    sub.request(1)
  }
}
