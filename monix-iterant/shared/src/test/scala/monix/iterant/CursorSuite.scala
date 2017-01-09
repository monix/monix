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

package monix.iterant

abstract class CursorSuite extends BaseTestSuite {
  def fromList(list: List[Int]): Cursor[Int]

  test("cursor.toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.toList == list
    }
  }

  test("cursor.drop(5).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.drop(5).toList == list.drop(5)
    }
  }

  test("cursor.drop(1000).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.drop(1000).toList == list.drop(1000)
    }
  }

  test("cursor.take(5).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.drop(5).toList == list.drop(5)
    }
  }

  test("cursor.take(1000).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.drop(1000).toList == list.drop(1000)
    }
  }

  test("cursor.take(5).drop(5).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.take(5).drop(5).toList == Nil
    }
  }

  test("cursor.drop(5).take(5).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.drop(5).take(5).toList == list.slice(5, 10)
    }
  }

  test("cursor.slice(5,5).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.slice(5,5).toList == list.slice(5,5)
    }
  }

  test("cursor.slice(5,10).toList") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.slice(5,10).toList == list.slice(5, 10)
    }
  }

  test("cursor.map") { _ =>
    check2 { (list: List[Int], f: Int => Int) =>
      val cursor = fromList(list)
      cursor.map(f).toList == list.map(f)
    }
  }

  test("cursor.filter") { _ =>
    check2 { (list: List[Int], f: Int => Boolean) =>
      val cursor = fromList(list)
      cursor.filter(f).toList == list.filter(f)
    }
  }

  test("cursor.collect") { _ =>
    check3 { (list: List[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val cursor = fromList(list)
      cursor.collect(pf).toList == list.collect(pf)
    }
  }

  test("cursor.toIterator") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      cursor.toIterator.toList == list
    }
  }

  test("cursor.toJavaIterator") { _ =>
    check1 { (list: List[Int]) =>
      import collection.JavaConverters._
      val cursor = fromList(list)
      cursor.toJavaIterator.asScala.toList == list
    }
  }

  test("cursor.hasMore") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      var seen1 = if (cursor.hasMore()) 1 else 0
      var seen2 = 0

      while (cursor.moveNext()) {
        seen2 += 1
        seen1 += (if (cursor.hasMore()) 1 else 0)
      }

      seen1 == seen2 && seen2 == list.length
    }
  }

  test("cursor.hasMore == list.nonEmpty") { _ =>
    check1 { (list: List[Int]) =>
      val cursor = fromList(list)
      list.nonEmpty == cursor.hasMore()
    }
  }
}

object ArrayCursorSuite extends CursorSuite {
  override def fromList(list: List[Int]): Cursor[Int] =
    Cursor.fromArray(list.toArray)
}

object ArraySliceCursorSuite extends CursorSuite {
  override def fromList(list: List[Int]): Cursor[Int] = {
    val listOf5 = (0 until 5).toList
    val fullList = listOf5 ::: list ::: listOf5
    val slice = fullList.slice(5, list.length + 5)
    Cursor.fromArray(slice.toArray)
  }
}

object IteratorCursorSuite extends CursorSuite {
  override def fromList(list: List[Int]): Cursor[Int] =
    Cursor.fromIterator(list.iterator)
}