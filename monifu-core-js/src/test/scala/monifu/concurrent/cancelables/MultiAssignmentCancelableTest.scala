/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.concurrent.cancelables

import scala.scalajs.test.JasmineTest

object MultiAssignmentCancelableTest extends JasmineTest {
  describe("MultiAssignmentCancelable") {
    it("should cancel()") {
      var effect = 0
      val sub = BooleanCancelable(effect += 1)
      val mSub = MultiAssignmentCancelable(sub)

      expect(effect).toBe(0)
      expect(sub.isCanceled).toBe(false)
      expect(mSub.isCanceled).toBe(false)

      mSub.cancel()
      expect(effect).toBe(1)
      expect(sub.isCanceled).toBe(true)
      expect(mSub.isCanceled).toBe(true)

      mSub.cancel()
      expect(effect).toBe(1)
      expect(sub.isCanceled).toBe(true)
      expect(mSub.isCanceled).toBe(true)
    }

    it("should cancel() after second assignment") {
      var effect = 0
      val sub = BooleanCancelable(effect += 1)
      val mSub = MultiAssignmentCancelable(sub)
      val sub2 = BooleanCancelable(effect += 10)
      mSub() = sub2

      expect(effect).toBe(0)
      expect(sub.isCanceled).toBe(false)
      expect(sub2.isCanceled).toBe(false)
      expect(mSub.isCanceled).toBe(false)

      mSub.cancel()
      expect(effect).toBe(10)
      expect(sub.isCanceled).toBe(false)
      expect(sub2.isCanceled).toBe(true)
      expect(mSub.isCanceled).toBe(true)
    }

    it("should automatically cancel assigned") {
      val mSub = MultiAssignmentCancelable()
      mSub.cancel()

      var effect = 0
      val sub = BooleanCancelable(effect += 1)

      expect(effect).toBe(0)
      expect(sub.isCanceled).toBe(false)
      expect(mSub.isCanceled).toBe(true)

      mSub() = sub
      expect(effect).toBe(1)
      expect(sub.isCanceled).toBe(true)
      expect(mSub.isCanceled).toBe(true)
    }
  }
}
