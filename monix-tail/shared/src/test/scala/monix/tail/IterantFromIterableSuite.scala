/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.eval.{Coeval, DummyException, Task}

import scala.util.Failure

object IterantFromIterableSuite extends BaseTestSuite {
  test("AsyncStream.fromIterable") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("AsyncStream.fromIterable (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromIterable(list) .mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("AsyncStream.fromIterator protects against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val iterator = new Iterator[Int] {
      def hasNext: Boolean = true
      def next(): Int = throw dummy
    }

    val result = AsyncStream.fromIterator(iterator).toListL.runAsync
    s.tick(); assertEquals(result.value, Some(Failure(dummy)))
  }

  test("LazyStream.fromIterable") { _ =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromIterable(list).toListL
      result === Coeval.now(list)
    }
  }

  test("LazyStream.fromIterable (async)") { _ =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromIterable(list) .mapEval(x => Coeval(x)).toListL
      result === Coeval.now(list)
    }
  }

  test("LazyStream.fromIterator protects against user code") { _ =>
    val dummy = DummyException("dummy")
    val iterator = new Iterator[Int] {
      def hasNext: Boolean = true
      def next(): Int = throw dummy
    }

    val result = LazyStream.fromIterator(iterator).toListL.runTry
    assertEquals(result, Failure(dummy))
  }
}
