/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest

object SingleAssignmentCancelableTest extends JasmineTest {
  describe("SingleAssignmentCancelable") {
    it("should cancel") {
      var effect = 0
      val s = SingleAssignmentCancelable()
      val b = BooleanCancelable { effect += 1 }
      s() = b

      s.cancel()
      expect(s.isCanceled).toBe(true)
      expect(b.isCanceled).toBe(true)
      expect(effect).toBe(1)

      s.cancel()
      expect(effect).toBe(1)
    }

    it("should cancel on single assignment") {
      val s = SingleAssignmentCancelable()
      s.cancel()
      expect(s.isCanceled).toBe(true)

      var effect = 0
      val b = BooleanCancelable { effect += 1 }
      s() = b

      expect(b.isCanceled).toBe(true)
      expect(effect).toBe(1)

      s.cancel()
      expect(effect).toBe(1)
    }

    it("should throw exception on multi assignment") {
      val s = SingleAssignmentCancelable()
      val b1 = BooleanCancelable.alreadyCanceled
      val b2 = BooleanCancelable.alreadyCanceled

      s() = b1
      expect(() => s() = b2).toThrow()
    }

    it("should throw exception on multi assignment when canceled") {
      val s = SingleAssignmentCancelable()
      s.cancel()

      val b1 = BooleanCancelable.alreadyCanceled
      s() = b1

      val b2 = BooleanCancelable.alreadyCanceled
      expect(() => s() = b2).toThrow()
    }
  }
}
