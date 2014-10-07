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
 
package monifu.concurrent.atomic

import scala.scalajs.test.JasmineTest

object AtomicAnyTest extends JasmineTest {

  describe("AtomicAny") {
    it("should set()") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      r.set("update")
      expect(r.get).toBe("update")
    }

    it("should getAndSet") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.getAndSet("update")).toBe("initial")
      expect(r.get).toBe("update")
    }

    it("should compareAndSet") {
      val r = Atomic("initial")
      expect(r.get).toBe("initial")

      expect(r.compareAndSet("initial", "update")).toBe(true)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("initial", "other") ).toBe(false)
      expect(r.get).toBe("update")
      expect(r.compareAndSet("update",  "other") ).toBe(true)
      expect(r.get).toBe("other")
    }

    it("should transform") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      r.transform(s => "updated" + s.dropWhile(_ != ' '))
      expect(r.get).toBe("updated value")
    }

    it("should transformAndGet") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndGet(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("updated value")
    }

    it("should getAndTransform") {
      val r = Atomic("initial value")
      expect(r()).toBe("initial value")

      val value = r.getAndTransform(s => "updated" + s.dropWhile(_ != ' '))
      expect(value).toBe("initial value")
      expect(r.get).toBe("updated value")
    }

    it("should transformAndExtract") {
      val r = Atomic("initial value")
      expect(r.get).toBe("initial value")

      val value = r.transformAndExtract { s =>
        val newS = "updated" + s.dropWhile(_ != ' ')
        ("extracted", newS)
      }

      expect(value).toBe("extracted")
      expect(r.get).toBe("updated value")
    }
  }
}
