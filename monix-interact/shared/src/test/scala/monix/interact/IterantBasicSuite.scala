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

package monix.interact

import monix.eval.{Coeval, Task}

object IterantBasicSuite extends BaseTestSuite {
  test("arbitraryListToTaskStream works") { implicit s =>
    check2 { (list: List[Int], i: Int) =>
      val stream = arbitraryListToTaskStream(list, math.abs(i % 4))
      stream.toListL === Task.now(list)
    }
  }

  test("arbitraryListToCoevalStream") { implicit s =>
    check2 { (list: List[Int], i: Int) =>
      val stream = arbitraryListToCoevalStream(list, math.abs(i % 4))
      stream.toListL === Coeval.now(list)
    }
  }
}
