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
 
package monifu.concurrent.utils

import scala.scalajs.test.JasmineTest
import scala.concurrent.Future
import concurrent.duration._
import monifu.concurrent.extensions._

object FutureExtensionsTest extends JasmineTest {
  import monifu.concurrent.Implicits.globalScheduler

  describe("FutureExtensions") {
    beforeEach {
      jasmine.Clock.useMock()
    }

    it("should do delayedResult") {
      val f = Future.delayedResult(50.millis)("TICK")
      jasmine.Clock.tick(40)

      expect(f.value.flatMap(_.toOption).getOrElse("")).toBe("")
      jasmine.Clock.tick(10)
      expect(f.value.flatMap(_.toOption).getOrElse("")).toBe("TICK")
    }

    it("should succeed on withTimeout") {
      val f = Future.delayedResult(50.millis)("Hello world!")
      val t = f.withTimeout(300.millis)

      jasmine.Clock.tick(50)
      expect(t.value.get.get).toBe("Hello world!")
    }

    it("should fail on withTimeout") {
      val f = Future.delayedResult(1.second)("Hello world!")
      val t = f.withTimeout(50.millis)

      jasmine.Clock.tick(50)
      expect(t.value.get.isFailure).toBe(true)
    }
  }
}
