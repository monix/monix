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

object RefCountCancelableTest extends JasmineTest {
  describe("RefCountCancelable") {
    it("should cancel without dependent references") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }
      sub.cancel()

      expect(sub.isCanceled).toBe(true)
      expect(isCanceled).toBe(true)
    }

    it("should execute onCancel with no active refs available") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }

      val s1 = sub.acquire()
      val s2 = sub.acquire()
      s1.cancel()
      s2.cancel()

      expect(isCanceled).toBe(false)
      expect(sub.isCanceled).toBe(false)

      sub.cancel()

      expect(isCanceled).toBe(true)
      expect(sub.isCanceled).toBe(true)
    }

    it("should execute onCancel only after all dependent refs have been canceled") {
      var isCanceled = false
      val sub = RefCountCancelable { isCanceled = true }

      val s1 = sub.acquire()
      val s2 = sub.acquire()
      sub.cancel()

      expect(sub.isCanceled).toBe(true)
      expect(isCanceled).toBe(false)
      s1.cancel()
      expect(isCanceled).toBe(false)
      s2.cancel()
      expect(isCanceled).toBe(true)
    }
  }
}
