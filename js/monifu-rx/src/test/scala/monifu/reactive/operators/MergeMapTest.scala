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

package monifu.reactive.operators

import monifu.concurrent.Implicits._
import monifu.reactive.Observable

import scala.scalajs.test.JasmineTest


object MergeMapTest extends JasmineTest {
  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.mergeMap") {
    it("should work") {
      val result2 =
        Observable.from(0 until 100).filter(_ % 5 == 0)
          .mergeMap(x => Observable.from(x until (x + 5)))
          .foldLeft(0)(_ + _).asFuture

      jasmine.Clock.tick(1)
      expect(result2.value.get.get.get).toBe((0 until 100).sum)
    }
  }
}
