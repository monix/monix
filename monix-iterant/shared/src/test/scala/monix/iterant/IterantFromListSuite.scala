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

import monix.eval.{Coeval, Task}

object IterantFromListSuite extends BaseTestSuite {
  test("AsyncStream.fromList") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromList(list).toListL
      result === Task.now(list)
    }
  }

  test("AsyncStream.fromList (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromList(list).mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("LazyStream.fromList") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromList(list).toListL
      result === Coeval.now(list)
    }
  }

  test("LazyStream.fromList (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromList(list).mapEval(x => Coeval(x)).toListL
      result === Coeval.now(list)
    }
  }
}
