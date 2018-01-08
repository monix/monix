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

object ChainedCancelableJVMSuite extends SimpleTestSuite {
  test("chain strong reference") {
    val source = ChainedCancelable()
    val child = ChainedCancelable()
    val b = BooleanCancelable()

    child.forwardTo(source)
    source.cancel()
    child := b

    assert(b.isCanceled)
  }

  test("chain weak reference") {
    def setupForward(child: ChainedCancelable): Unit = {
      val source = ChainedCancelable()
      child.forwardTo(source)
      source.cancel()
    }

    var tries = 0
    var success = false

    while (tries < 10 && !success) {
      val b = BooleanCancelable()
      val child = ChainedCancelable()
      setupForward(child)

      Runtime.getRuntime.gc()
      child := b
      success = !b.isCanceled
      tries += 1
    }

    assert(success)
  }

  test("chain second time before weak reference is collected") {
    val source1 = ChainedCancelable()
    val source2 = ChainedCancelable()

    val child = ChainedCancelable()
    val b = BooleanCancelable()

    child.forwardTo(source1)
    source1.cancel()
    child.forwardTo(source2)
    child := b

    assert(b.isCanceled)
  }

  test("chain second time after weak reference is collected") {
    def setupForward(child: ChainedCancelable): Unit = {
      val source1 = ChainedCancelable()
      child.forwardTo(source1)
      source1.cancel()
    }

    var tries = 0
    var success = false

    while (tries < 10 && !success) {
      val source2 = ChainedCancelable()

      val child = ChainedCancelable()
      val b = BooleanCancelable()

      setupForward(child)
      Runtime.getRuntime.gc()

      child.forwardTo(source2)
      child := b

      success = !b.isCanceled
      source2.cancel()
      success = success && b.isCanceled
      tries += 1
    }

    assert(success)
  }

  test("chain of weak references") {
    def setupForward(child: ChainedCancelable): Unit = {
      val source1 = ChainedCancelable()
      child.forwardTo(source1)
      source1.cancel()
    }

    var tries = 0
    var success = false

    while (tries < 10 && !success) {
      val source = ChainedCancelable()
      setupForward(source)

      val child = ChainedCancelable()
      val b = BooleanCancelable()

      System.gc()
      child.forwardTo(source)
      child := b

      success = !b.isCanceled
      source.cancel()
      success = success && !b.isCanceled

      tries += 1
    }

    assert(success)
  }
}
