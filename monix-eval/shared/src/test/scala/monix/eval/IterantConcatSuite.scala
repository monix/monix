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

object IterantConcatSuite extends BaseTestSuite {
  test("prepend") { implicit s =>
    check2 { (list: List[Int], a: Int) =>
      val source = Iterant.fromList(list)
      val received = a #:: source
      val expected = Iterant.fromList(a :: list)
      received === expected
    }
  }

  test("concat") { implicit s =>
    check2 { (list1: List[Int], list2: List[Int]) =>
      val iter1 = Iterant.fromList(list1)
      val iter2 = Iterant.fromList(list2)
      val received = iter1 ++ iter2
      val expected = Iterant.fromList(list1 ::: list2)
      received === expected
    }
  }

  test("concat task") { implicit s =>
    check2 { (list1: List[Int], list2: List[Int]) =>
      val iter1 = Iterant.fromList(list1)
      val iter2 = Task.eval(Iterant.fromList(list2))
      val received = iter1 ++ iter2
      val expected = Iterant.fromList(list1 ::: list2)
      received === expected
    }
  }
}
