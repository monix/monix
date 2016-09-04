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

object StreamFromIterableSuite extends BaseTestSuite {
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

  test("TaskStream.fromIterator is memoized") { implicit s =>
    def findCons(stream: Stream[Task, Int]): Stream.Cons[Task, Int] =
      stream match {
        case ref @ Stream.Cons(_,_,_) => ref
        case Stream.Wait(rest,_) =>
          val ref = rest.runAsync; s.tick()
          findCons(ref.value.get.get)
        case other =>
          throw new IllegalStateException(s"Unexpected: $other")
      }

    val source = TaskStream.fromIterator(List(1,2,3).iterator)
    val cons = findCons(source.stream)

    val result1 = cons.tail.runAsync
    val result2 = cons.tail.runAsync

    s.tick()
    assertEquals(result1.value, result2.value)
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

  test("CoevalStream.fromIterator is memoized") { _ =>
    def findCons(stream: Stream[Coeval, Int]): Stream.Cons[Coeval, Int] =
      stream match {
        case ref @ Stream.Cons(_,_,_) => ref
        case Stream.Wait(rest,_) =>
          findCons(rest.value)
        case other =>
          throw new IllegalStateException(s"Unexpected: $other")
      }

    val source = CoevalStream.fromIterator(List(1,2,3).iterator)
    val cons = findCons(source.stream)

    val result1 = cons.tail.value
    val result2 = cons.tail.value
    assertEquals(result1, result2)
  }
}
