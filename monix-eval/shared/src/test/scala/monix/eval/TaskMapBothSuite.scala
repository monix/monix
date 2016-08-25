/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.eval

import scala.util.Success

object TaskMapBothSuite extends BaseTestSuite {
  test("sum two async tasks") { implicit s =>
    val ta = Task(1)
    val tb = Task(2)

    val r = Task.mapBoth(ta, tb)(_ + _)
    val f = r.runAsync; s.tick()
    assertEquals(f.value.get, Success(3))
  }

  test("sum two synchronous tasks") { implicit s =>
    val ta = Task.eval(1)
    val tb = Task.eval(2)

    val r = Task.mapBoth(ta, tb)(_ + _)
    val f = r.runAsync; s.tick()
    assertEquals(f.value.get, Success(3))
  }

  test("should be stack-safe for synchronous tasks") { implicit s =>
    val count = 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val init = Task.eval(0L)

    val sum = tasks.foldLeft(init)((acc,t) => Task.mapBoth(acc, t)(_ + _))
    val result = sum.runAsync

    s.tick()
    assertEquals(result.value.get, Success(count * (count-1) / 2))
  }

  test("should be stack-safe for asynchronous tasks") { implicit s =>
    val count = 10000
    val tasks = (0 until count).map(x => Task(x))
    val init = Task.eval(0L)

    val sum = tasks.foldLeft(init)((acc,t) => Task.mapBoth(acc, t)(_ + _))
    val result = sum.runAsync

    s.tick()
    assertEquals(result.value.get, Success(count * (count-1) / 2))
  }

  test("sum random synchronous tasks") { implicit s =>
    check1 { (numbers: List[Int]) =>
      val sum = numbers.foldLeft(Task.now(0))((acc,t) => Task.mapBoth(acc, Task.eval(t))(_+_))
      sum === Task.now(numbers.sum)
    }
  }

  test("sum random asynchronous tasks") { implicit s =>
    check1 { (numbers: List[Int]) =>
      val sum = numbers.foldLeft(Task(0))((acc,t) => Task.mapBoth(acc, Task(t))(_+_))
      sum === Task(numbers.sum)
    }
  }
}
