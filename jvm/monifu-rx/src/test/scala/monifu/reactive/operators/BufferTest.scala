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

import monifu.reactive.Observable
import org.scalatest.FunSpec
import concurrent.duration._
import scala.concurrent.Await
import monifu.concurrent.Implicits.globalScheduler

class BufferTest extends FunSpec {
  describe("Observable.buffer(count)") {
    it("should work") {
      val f1 = Observable.range(0,100).buffer(50).foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some(Seq(0 until 50, 50 until 100)))

      val f2 = Observable.range(0, 100).buffer(2).foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture
      assert(Await.result(f2, 2.seconds) === Some((0 until 100).filter(_ % 2 == 0).map(x => Seq(x, x+1))))
    }
  }

  describe("Observable.bufferTimed(timespan)") {
    it("should work") {
      val f = Observable.range(0,100).bufferTimed(200.millis).asFuture
      assert(Await.result(f, 5.seconds) === Some(0 until 100))
    }
  }

  describe("Observable.bufferSizedAndTimed(count,timespan)") {
    it("should work") {
      val f1 = Observable.range(0,100).bufferSizedAndTimed(50, 2.second)
        .foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture

      assert(Await.result(f1, 2.seconds) === Some(Seq(0 until 50, 50 until 100)))

      val f2 = Observable.range(0, 100).bufferSizedAndTimed(2, 2.second)
        .foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture

      assert(Await.result(f2, 2.seconds) === Some((0 until 100).filter(_ % 2 == 0).map(x => Seq(x, x+1))))
    }
  }
}
