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

import monix.tail.batches._
import org.scalacheck.Arbitrary
import scala.reflect.ClassTag

abstract class BatchCursorSuite[A : ClassTag](implicit
  arbA: Arbitrary[A],
  arbAtoA: Arbitrary[A => A],
  arbAtoBoolean: Arbitrary[A => Boolean])
  extends BaseTestSuite {

  type Cursor <: BatchCursor[A]
  
  def fromList(list: List[A]): Cursor

  test("cursor.toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.toList == list
    }
  }

  test("cursor.toArray") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.toArray.toList == list
    }
  }

  test("cursor.drop(2).toArray") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(2).toArray.toList == list.drop(2)
    }
  }

  test("cursor.take(2).toArray") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.take(2).toArray.toList == list.take(2)
    }
  }

  test("cursor.toGenerator") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.toGenerator.cursor().toList == list
    }
  }

  test("cursor.drop(2).toGenerator") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(2).toGenerator.cursor().toList == list.drop(2)
    }
  }

  test("cursor.take(2).toGenerator") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.take(2).toGenerator.cursor().toList == list.take(2)
    }
  }

  test("cursor.drop(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(5).toList == list.drop(5)
    }
  }

  test("cursor.drop(1000).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(1000).toList == list.drop(1000)
    }
  }

  test("cursor.take(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(5).toList == list.drop(5)
    }
  }

  test("cursor.take(1000).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(1000).toList == list.drop(1000)
    }
  }

  test("cursor.take(5).drop(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.take(5).drop(5).toList == Nil
    }
  }

  test("cursor.drop(5).take(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.drop(5).take(5).toList == list.slice(5, 10)
    }
  }

  test("cursor.slice(5,5).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.slice(5,5).toList == list.slice(5,5)
    }
  }

  test("cursor.slice(5,10).toList") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.slice(5,10).toList == list.slice(5, 10)
    }
  }

  test("cursor.map") { _ =>
    check2 { (list: List[A], f: A => A) =>
      val cursor = fromList(list)
      cursor.map(f).toList == list.map(f)
    }
  }

  test("cursor.filter") { _ =>
    check2 { (list: List[A], f: A => Boolean) =>
      val cursor = fromList(list)
      cursor.filter(f).toList == list.filter(f)
    }
  }

  test("cursor.collect") { _ =>
    check3 { (list: List[A], p: A => Boolean, f: A => A) =>
      val pf: PartialFunction[A, A] = { case x if p(x) => f(x) }
      val cursor = fromList(list)
      cursor.collect(pf).toList == list.collect(pf)
    }
  }

  test("cursor.toIterator") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.toIterator.toList == list
    }
  }

  test("cursor.hasNext") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      var seen = 0

      while (cursor.hasNext()) {
        cursor.next()
        seen += 1
      }

      seen == list.length
    }
  }

  test("cursor.hasNext <=> !cursor.isEmpty") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.hasNext() == !cursor.isEmpty
    }
  }

  test("cursor.hasNext <=> cursor.nonEmpty") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.hasNext() == cursor.nonEmpty
    }
  }

  test("cursor.hasNext == list.nonEmpty") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      list.nonEmpty == cursor.hasNext()
    }
  }

  test("recommendedBatchSize is positive") { _ =>
    check1 { (list: List[A]) =>
      val cursor = fromList(list)
      cursor.recommendedBatchSize > 0
    }
  }
}

object ArrayCursorSuite extends BatchCursorSuite[Int] {
  type Cursor = ArrayCursor[Int]

  override def fromList(list: List[Int]): Cursor =
    BatchCursor.fromArray(list.toArray)
}

object ArraySliceCursorSuite extends BatchCursorSuite[Int] {
  type Cursor = ArrayCursor[Int]

  override def fromList(list: List[Int]): Cursor = {
    val listOf5 = (0 until 5).toList
    val fullList = listOf5 ::: list ::: listOf5
    BatchCursor.fromArray(fullList.toArray, 5, list.length)
  }
}

object IteratorCursorSuite extends BatchCursorSuite[Int] {
  type Cursor = BatchCursor[Int]

  override def fromList(list: List[Int]): Cursor =
    BatchCursor.fromIterator(list.iterator)
}

object BooleansCursorSuite extends BatchCursorSuite[Boolean] {
  type Cursor = BooleansCursor

  override def fromList(list: List[Boolean]): BooleansCursor =
    BatchCursor.booleans(list.toArray)
}

object BytesCursorSuite extends BatchCursorSuite[Byte] {
  type Cursor = BytesCursor

  override def fromList(list: List[Byte]): BytesCursor =
    BatchCursor.bytes(list.toArray)
}

object CharsCursorSuite extends BatchCursorSuite[Char] {
  type Cursor = CharsCursor

  override def fromList(list: List[Char]): CharsCursor =
    BatchCursor.chars(list.toArray)
}

object IntegersCursorSuite extends BatchCursorSuite[Int] {
  type Cursor = IntegersCursor

  override def fromList(list: List[Int]): IntegersCursor =
    BatchCursor.integers(list.toArray)
}

object LongsCursorSuite extends BatchCursorSuite[Long] {
  type Cursor = LongsCursor

  override def fromList(list: List[Long]): LongsCursor =
    BatchCursor.longs(list.toArray)
}

object DoublesCursorSuite extends BatchCursorSuite[Double] {
  type Cursor = DoublesCursor

  override def fromList(list: List[Double]): DoublesCursor =
    BatchCursor.doubles(list.toArray)
}