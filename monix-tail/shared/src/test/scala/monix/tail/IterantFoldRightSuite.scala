/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.eval.Coeval

object IterantFoldRightSuite extends BaseTestSuite {
  def exists(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(false)) { (e, next) =>
      if (p(e)) Coeval(true) else next
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(true)) { (e, next) =>
      if (!p(e)) Coeval(false) else next
    }

  test("foldRight can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val iter = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      exists(iter, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldRight can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val iter = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forall(iter, p) <-> Coeval(list.forall(p))
    }
  }
}
