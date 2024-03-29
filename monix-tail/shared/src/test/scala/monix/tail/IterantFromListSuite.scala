/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.eval.{ Coeval, Task }

object IterantFromListSuite extends BaseTestSuite {
  test("Iterant[Task].fromList") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Task].fromList(list).toListL
      result <-> Task.now(list)
    }
  }

  test("Iterant[Task].fromList (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Task].fromList(list).mapEval(x => Task.evalAsync(x)).toListL
      result <-> Task.now(list)
    }
  }

  test("Iterant[Coeval].fromList") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromList(list).toListL
      result <-> Coeval.now(list)
    }
  }

  test("Iterant[Coeval].fromList (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromList(list).mapEval(x => Coeval(x)).toListL
      result <-> Coeval.now(list)
    }
  }
}
