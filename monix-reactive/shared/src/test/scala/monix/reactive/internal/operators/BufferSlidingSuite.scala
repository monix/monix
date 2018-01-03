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

package monix.reactive.internal.operators

import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.reactive.{BaseTestSuite, Observable}
import scala.concurrent.duration._
import scala.util.Success

object BufferSlidingSuite extends BaseTestSuite {
  test("bufferSliding equivalence with the standard library") { implicit s =>
    check3 { (numbers: List[Int], countR: Int, skipR: Int) =>
      val count = Math.floorMod(countR, 10) + 1
      val skip = Math.floorMod(skipR, 10) + 1

      val received = Observable.fromIterable(numbers).bufferSliding(count, skip).map(_.toList).toListL
      val expected = Task.now(numbers.sliding(count, skip).map(_.toList).toList)
      received <-> expected
    }
  }

  test("issue #275 regression test") { implicit s =>
    val received = Observable.interval(1.seconds).bufferSliding(5, 1).take(10).map(_.toList).toListL.runAsync
    val expected = (0 until 20).sliding(5,1).take(10).map(_.toList).toList
    s.tick(100.seconds)

    assertEquals(received.value, Some(Success(expected)))
  }
}
