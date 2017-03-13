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

package monix.eval

import monix.execution.exceptions.DummyException

import scala.collection.mutable.ListBuffer
import scala.util.Failure

object IterantFromCollectionSuite extends BaseTestSuite {
  test("Iterant.fromIndexedSeq") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromIndexedSeq(list.toVector).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromIterable") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromIterable (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromIterable(list) .mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromIterator protects against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val iterator = new Iterator[Int] {
      def hasNext: Boolean = true
      def next(): Int = throw dummy
    }

    val result = Iterant.fromIterator(iterator).toListL.runAsync
    s.tick(); assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant.fromList") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromList(list).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromList (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromList(list).mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromSeq(vector)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromSeq(list.toVector).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromSeq(list)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromSeq(list).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.fromSeq(iterable)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromSeq(list.to[ListBuffer]).toListL
      result === Task.now(list)
    }
  }

}
