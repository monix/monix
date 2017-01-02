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

import scala.util.Failure

object StreamableFromIterableSuite extends BaseTestSuite {
  test("TaskStream.fromIterable") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.fromIterable (batched)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list, batchSize = 4).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.fromIterable (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list) .mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.fromIterator protects against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val iterator = new Iterator[Int] {
      def hasNext: Boolean = true
      def next(): Int = throw dummy
    }

    val result = TaskStream.fromIterator(iterator).toListL.runAsync
    s.tick(); assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.fromIterable should throw error if batchSize <= 0") { _ =>
    intercept[IllegalArgumentException] {
      TaskStream.fromIterable(List(1,2,3), 0)
    }
  }

  test("TaskStream.fromIterator should throw error if batchSize <= 0") { _ =>
    intercept[IllegalArgumentException] {
      TaskStream.fromIterator(List(1,2,3).iterator, 0)
    }
  }

  test("CoevalStream.fromIterable") { _ =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.fromIterable (batched)") { _ =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list, batchSize = 4).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.fromIterable (async)") { _ =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list) .mapEval(x => Coeval(x)).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.fromIterator protects against user code") { _ =>
    val dummy = DummyException("dummy")
    val iterator = new Iterator[Int] {
      def hasNext: Boolean = true
      def next(): Int = throw dummy
    }

    val result = CoevalStream.fromIterator(iterator).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.fromIterable should throw error if batchSize <= 0") { _ =>
    intercept[IllegalArgumentException] {
      CoevalStream.fromIterable(List(1,2,3), 0)
    }
  }

  test("CoevalStream.fromIterator should throw error if batchSize <= 0") { _ =>
    intercept[IllegalArgumentException] {
      CoevalStream.fromIterator(List(1,2,3).iterator, 0)
    }
  }
}
