/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

object CompositeCancelableSuite extends SimpleTestSuite {
  test("cancel") {
    val s = CompositeCancelable()
    val b1 = BooleanCancelable()
    val b2 = BooleanCancelable()
    s += b1
    s += b2
    s.cancel()

    assert(s.isCanceled)
    assert(b1.isCanceled)
    assert(b2.isCanceled)
  }

  test("cancel on assignment after being canceled") {
    val s = CompositeCancelable()
    val b1 = BooleanCancelable()
    s += b1
    s.cancel()

    val b2 = BooleanCancelable()
    s += b2

    assert(s.isCanceled)
    assert(b1.isCanceled)
    assert(b2.isCanceled)
  }
}
