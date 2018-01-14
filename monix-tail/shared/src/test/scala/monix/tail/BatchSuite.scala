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

abstract class BatchSuite[A : ClassTag](implicit
  arbA: Arbitrary[A],
  arbAtoA: Arbitrary[A => A],
  arbAtoBoolean: Arbitrary[A => Boolean])
  extends BaseTestSuite {
 
  type Batch <: batches.Batch[A]

  def fromList(list: List[A]): Batch

  test("batch.toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.toList == list
    }
  }

  test("batch.toArray") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.toArray.toList == list
    }
  }

  test("batch.drop(2).toArray") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(2).toArray.toList == list.drop(2)
    }
  }

  test("batch.take(2).toArray") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.take(2).toArray.toList == list.take(2)
    }
  }

  test("batch.drop(2).toIterable") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(2).toIterable.toList == list.drop(2)
    }
  }

  test("batch.take(2).toIterable") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.take(2).toIterable.toList == list.take(2)
    }
  }

  test("batch.drop(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(5).toList == list.drop(5)
    }
  }

  test("batch.drop(1000).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(1000).toList == list.drop(1000)
    }
  }

  test("batch.take(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(5).toList == list.drop(5)
    }
  }

  test("batch.take(1000).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(1000).toList == list.drop(1000)
    }
  }

  test("batch.take(5).drop(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.take(5).drop(5).toList == Nil
    }
  }

  test("batch.drop(5).take(5).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.drop(5).take(5).toList == list.slice(5, 10)
    }
  }

  test("batch.slice(5,5).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.slice(5,5).toList == list.slice(5,5)
    }
  }

  test("batch.slice(5,10).toList") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.slice(5,10).toList == list.slice(5, 10)
    }
  }

  test("batch.map") { _ =>
    check2 { (list: List[A], f: A => A) =>
      val batch = fromList(list)
      batch.map(f).toList == list.map(f)
    }
  }

  test("batch.filter") { _ =>
    check2 { (list: List[A], f: A => Boolean) =>
      val batch = fromList(list)
      batch.filter(f).toList == list.filter(f)
    }
  }

  test("batch.collect") { _ =>
    check3 { (list: List[A], p: A => Boolean, f: A => A) =>
      val pf: PartialFunction[A, A] = { case x if p(x) => f(x) }
      val batch = fromList(list)
      batch.collect(pf).toList == list.collect(pf)
    }
  }

  test("batch.toIterable") { _ =>
    check1 { (list: List[A]) =>
      val batch = fromList(list)
      batch.toIterable.toList == list
    }
  }
}

object ArrayBatchSuite extends BatchSuite[Int] {
  type Batch = ArrayBatch[Int]

  override def fromList(list: List[Int]): Batch =
    Batch.fromArray(list.toArray)
}

object ArraySliceBatchSuite extends BatchSuite[Int] {
  type Batch = ArrayBatch[Int]

  override def fromList(list: List[Int]): Batch = {
    val listOf5 = (0 until 5).toList
    val fullList = listOf5 ::: list ::: listOf5
    Batch.fromArray(fullList.toArray, 5, list.length)
  }
}

object BatchIterableSuite extends BatchSuite[Int] {
  type Batch = batches.Batch[Int]

  override def fromList(list: List[Int]): Batch =
    Batch.fromIterable(list, 4)
}

object SeqBatchSuite extends BatchSuite[Int] {
  type Batch = SeqBatch[Int]

  override def fromList(list: List[Int]): Batch =
    new SeqBatch[Int](list, 4)
}

object BooleansBatchSuite extends BatchSuite[Boolean] {
  type Batch = BooleansBatch

  override def fromList(list: List[Boolean]): BooleansBatch =
    Batch.booleans(list.toArray)
}

object BytesBatchSuite extends BatchSuite[Byte] {
  type Batch = BytesBatch

  override def fromList(list: List[Byte]): BytesBatch =
    Batch.bytes(list.toArray)
}

object CharsBatchSuite extends BatchSuite[Char] {
  type Batch = CharsBatch

  override def fromList(list: List[Char]): CharsBatch =
    Batch.chars(list.toArray)
}

object IntegersBatchSuite extends BatchSuite[Int] {
  type Batch = IntegersBatch

  override def fromList(list: List[Int]): IntegersBatch =
    Batch.integers(list.toArray)
}

object LongsBatchSuite extends BatchSuite[Long] {
  type Batch = LongsBatch

  override def fromList(list: List[Long]): LongsBatch =
    Batch.longs(list.toArray)
}

object DoublesBatchSuite extends BatchSuite[Double] {
  type Batch = DoublesBatch

  override def fromList(list: List[Double]): DoublesBatch =
    Batch.doubles(list.toArray)
}
