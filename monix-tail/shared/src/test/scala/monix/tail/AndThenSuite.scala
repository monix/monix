/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import monix.execution.internal.Platform
import monix.tail.internal.AndThen

object AndThenSuite extends BaseTestSuite {
  test("compose a chain of functions with andThen") { _ =>
    check2 { (i: Int, fs: List[Int => Int]) =>
      val result = fs.map(AndThen(_)).reduceOption(_.andThen(_)).map(_(i))
      val expect = fs.reduceOption(_.andThen(_)).map(_(i))

      result == expect
    }
  }

  test("compose a chain of functions with compose") { _ =>
    check2 { (i: Int, fs: List[Int => Int]) =>
      val result = fs.map(AndThen(_)).reduceOption(_.compose(_)).map(_(i))
      val expect = fs.reduceOption(_.compose(_)).map(_(i))

      result == expect
    }
  }

  test("andThen is stack safe") { _ =>
    val count = if (Platform.isJVM) 500000 else 1000
    val fs = (0 until count).map(_ => { i: Int => i + 1 })
    val result = fs.foldLeft(AndThen((x: Int) => x))(_.andThen(_))(42)

    assertEquals(result, count + 42)
  }

  test("compose is stack safe") { _ =>
    val count = if (Platform.isJVM) 500000 else 1000
    val fs = (0 until count).map(_ => { i: Int => i + 1 })
    val result = fs.foldLeft(AndThen((x: Int) => x))(_.compose(_))(42)

    assertEquals(result, count + 42)
  }

  test("Function1 andThen is stack safe") { _ =>
    val count = if (Platform.isJVM) 50000 else 1000
    val start: (Int => Int) = AndThen((x: Int) => x)
    val fs = (0 until count).foldLeft(start) { (acc, _) =>
      acc.andThen(_ + 1)
    }
    assertEquals(fs(0), count)
  }

  test("toString") { _ =>
    assert(AndThen((x: Int) => x).toString.startsWith("AndThen$"))
  }
}
