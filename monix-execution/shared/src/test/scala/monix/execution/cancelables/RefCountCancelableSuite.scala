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

object RefCountCancelableSuite extends SimpleTestSuite {
  test("cancel without dependent references") {
    var isCanceled = false
    val sub = RefCountCancelable { () => isCanceled = true }
    sub.cancel()

    assert(isCanceled)
  }

  test("execute onCancel with no dependent refs active") {
    var isCanceled = false
    val sub = RefCountCancelable { () => isCanceled = true }

    val s1 = sub.acquire()
    val s2 = sub.acquire()
    s1.cancel()
    s2.cancel()

    assert(!isCanceled)
    assert(!sub.isCanceled)

    sub.cancel()

    assert(isCanceled)
    assert(sub.isCanceled)
  }

  test("execute onCancel only after all dependent refs have been canceled") {
    var isCanceled = false
    val sub = RefCountCancelable { () => isCanceled = true }

    val s1 = sub.acquire()
    val s2 = sub.acquire()
    sub.cancel()

    assert(sub.isCanceled)
    assert(!isCanceled)
    s1.cancel()
    assert(!isCanceled)
    s2.cancel()
    assert(isCanceled)
  }
}
