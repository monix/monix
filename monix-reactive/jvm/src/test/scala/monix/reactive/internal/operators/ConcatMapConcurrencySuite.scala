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

import monix.reactive.{BaseConcurrencySuite, Observable}
import scala.concurrent.Await
import scala.concurrent.duration._

object ConcatMapConcurrencySuite extends BaseConcurrencySuite {
  test("concatMap should work for synchronous children") { implicit s =>
    val count = 10000L
    val expected = 3L * count * (count - 1) / 2

    for (_ <- 0 until 100) {
      val sum = Observable.range(0, count)
        .flatMap(x => Observable(x,x,x))
        .sumL
        .runAsync

      val result = Await.result(sum, 30.seconds)
      assertEquals(result, expected)
    }
  }

  test("concatMap should work for asynchronous children") { implicit s =>
    val count = 10000L
    val expected = 3L * count * (count - 1) / 2

    for (_ <- 0 until 100) {
      val sum = Observable.range(0, count)
        .flatMap(x => Observable(x,x,x).executeWithFork)
        .sumL
        .runAsync

      val result = Await.result(sum, 30.seconds)
      assertEquals(result, expected)
    }
  }
}
