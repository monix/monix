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

object CompositeCancelableTest extends JasmineTest {
  describe("CompositeCancelable") {
    it("should cancel") {
      val s = CompositeCancelable()
      val b1 = BooleanCancelable()
      val b2 = BooleanCancelable()
      s += b1
      s += b2
      s.cancel()

      expect(s.isCanceled).toBe(true)
      expect(b1.isCanceled).toBe(true)
      expect(b2.isCanceled).toBe(true)
    }

    it("should cancel on assignment after being canceled") {
      val s = CompositeCancelable()
      val b1 = BooleanCancelable()
      s += b1
      s.cancel()

      val b2 = BooleanCancelable()
      s += b2

      expect(s.isCanceled).toBe(true)
      expect(b1.isCanceled).toBe(true)
      expect(b2.isCanceled).toBe(true)
    }
  }
}
