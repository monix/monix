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

package monix.tail

import monix.eval.{Coeval, Task}

import concurrent.duration._
import scala.util.Success

object IterantRangesSuite extends BaseTestSuite {
  test("Iterant.range(0, 10, 1)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10).toListL.value
    assertEquals(lst, (0 until 10).toList)
  }

  test("Iterant.range(0, 10, 2)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10, 2).toListL.value
    assertEquals(lst, 0.until(10, 2).toList)
  }

  test("Iterant.range(10, 0, -1)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0, -1).toListL.value
    assertEquals(lst, 10.until(0, -1).toList)
  }

  test("Iterant.range(10, 0, -2)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0, -2).toListL.value
    assertEquals(lst, 10.until(0, -2).toList)
  }

  test("Iterant.range(0, 10, -1)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10, -1).toListL.value
    assertEquals(lst, 0.until(10, -1).toList)
  }

  test("Iterant.range(10, 0, 1)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0).toListL.value
    assertEquals(lst, 10.until(0, 1).toList)
  }

  test("Iterant.intervalWithFixedDelay(1.second, 2.seconds)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task].intervalWithFixedDelay(1.second, 2.seconds)
      .map { e => effect += 1; e }
      .take(5)
      .toListL
      .runAsync

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(1.second)
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0, 1, 2, 3, 4))))
  }

  test("Iterant.intervalWithFixedDelay(2.seconds)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task].intervalWithFixedDelay(2.seconds)
      .map { e => effect += 1; e }
      .take(5)
      .toListL
      .runAsync

    assertEquals(lst.value, None)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0, 1, 2, 3, 4))))
  }

  test("Iterant.intervalAtFixedRate(1.second)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task].intervalAtFixedRate(1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(100.millis))
      .take(3)
      .toListL
      .runAsync

    assertEquals(lst.value, None)
    assertEquals(effect, 0)
    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0, 1, 2))))
  }

  test("Iterant.intervalAtFixedRate(2.seconds, 1.second)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task].intervalAtFixedRate(2.seconds, 1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(100.millis))
      .take(3)
      .toListL
      .runAsync

    assertEquals(lst.value, None)
    s.tick(2.seconds)
    assertEquals(effect, 0)

    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0, 1, 2))))
  }

  test("Iterant.intervalAtFixedRate accounts for time it takes task to finish") { implicit s =>
    var effect = 0
    val lst = Iterant[Task].intervalAtFixedRate(1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(2.seconds))
      .take(3)
      .toListL
      .runAsync

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(2.seconds)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0, 1, 2))))
  }
}
