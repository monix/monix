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

import monix.tail.cursors.Generator

abstract class GeneratorSuite extends BaseTestSuite {
  def fromList(list: List[Int]): Generator[Int]

  test("cursor().toList") { _ =>
    check1 { (list: List[Int]) =>
      val generator = fromList(list)
      val list1 = generator.cursor().toList
      val list2 = generator.cursor().toList
      list == list1 && list1 == list2
    }
  }

  test("toList") { _ =>
    check1 { (list: List[Int]) =>
      val generator = fromList(list)
      val list1 = generator.toList
      val list2 = generator.toList
      list == list1 && list1 == list2
    }
  }

  test("toArray") { _ =>
    check1 { (list: List[Int]) =>
      val generator = fromList(list)
      val list1 = generator.toArray.toList
      val list2 = generator.toArray.toList
      list == list1 && list1 == list2
    }
  }

  test("toIterable") { _ =>
    check1 { (list: List[Int]) =>
      val generator = fromList(list)
      val list1 = generator.toIterable.toList
      val list2 = generator.toIterable.toList
      list == list1 && list1 == list2
    }
  }

  test("transform(_.map(f)).toList") { _ =>
    check2 { (list: List[Int], f: Int => Int) =>
      val source = fromList(list)
      val gen1 = source.transform(_.map(f))
      val l11  = gen1.cursor().toList
      val l12  = gen1.cursor().toList
      val gen2 = source.transform(_.map(f))
      val l21  = gen2.cursor().toList

      list.map(f) == l11 && l11 == l12 && l12 == l21
    }
  }
}

object ArrayGeneratorSuite extends GeneratorSuite {
  def fromList(list: List[Int]): Generator[Int] =
    Generator.fromArray(list.toArray)
}

object ArraySliceGeneratorSuite extends GeneratorSuite {
  def fromList(list: List[Int]): Generator[Int] = {
    val listOf5 = (0 until 5).toList
    val fullList = listOf5 ::: list ::: listOf5
    Generator.fromArray(fullList.toArray, 5, list.length)
  }
}

object IterableGeneratorSuite extends GeneratorSuite {
  def fromList(list: List[Int]): Generator[Int] =
    Generator.fromIterable(list)
}
