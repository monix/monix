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

import cats.laws._
import cats.laws.discipline._
import monix.eval.{Coeval, Task}

object IterantConcatSuite extends BaseTestSuite {
  test("Iterant.prepend") { implicit s =>
    check2 { (list: List[Int], a: Int) =>
      val source = Iterant[Task].fromList(list)
      val received = a +: source
      val expected = Iterant[Task].fromList(a :: list)
      received <-> expected
    }
  }

  test("Iterant ++ Iterant") { implicit s =>
    check2 { (list1: List[Int], list2: List[Int]) =>
      val i1 = Iterant[Task].fromList(list1)
      val i2 = Iterant[Task].fromList(list2)
      val received = i1 ++ i2
      val expected = Iterant[Task].fromList(list1 ::: list2)
      received <-> expected
    }
  }

  test("Iterant ++ F(Iterant)") { implicit s =>
    check2 { (list1: List[Int], list2: List[Int]) =>
      val i1 = Iterant[Task].fromList(list1)
      val i2 = Iterant[Task].fromList(list2)
      val received = i1 ++ Task.eval(i2)
      val expected = Iterant[Task].fromList(list1 ::: list2)
      received <-> expected
    }
  }

  test("Iterant :+ is consistent with ++") { implicit s =>
    check2 { (i: Iterant[Coeval, Int], e: Int) =>
      (i :+ e) <-> (i ++ Iterant[Coeval].pure(e))
    }
  }
}
