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
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.Observable

import scala.scalajs.test.JasmineTest


object MapTest extends JasmineTest {
  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.map") {
    it("should work") {
      val f = Observable.from(0 until 10).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      jasmine.Clock.tick(1)

      expect(f.value.get.get.get.mkString(",")).toBe((1 to 10).mkString(","))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      var result = ""
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after Cancel")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          Cancel
        }
      )

      jasmine.Clock.tick(1)
      expect(result).toBe("Test exception")
    }

    it("should cancel when downstream has canceled") {
      var wasCompleted = false
      Observable.repeat(1).doOnComplete { wasCompleted = true }
        .map(x => x).take(1000).subscribe()

      jasmine.Clock.tick(1)
      expect(wasCompleted).toBe(true)
    }
  }
}