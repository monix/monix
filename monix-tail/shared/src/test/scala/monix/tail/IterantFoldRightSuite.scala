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

import cats.syntax.all._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantFoldRightSuite extends BaseTestSuite {
  def exists(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(false)) { (e, next, stop) =>
      if (p(e)) stop >> Coeval(true) else next
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(true)) { (e, next, stop) =>
      if (!p(e)) stop >> Coeval(false) else next
    }

  test("foldRightL can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      exists(stream, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldRightL can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forall(stream, p) <-> Coeval(list.forall(p))
    }
  }

  test("foldRightL can short-circuit") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4).doOnEarlyStop(Coeval { effect += 1 })

    val r1 = exists(ref, _ == 6)
    assertEquals(r1.runTry, Success(false))
    assertEquals(effect, 0)

    val r2 = exists(ref, _ == 3)
    assertEquals(r2.runTry, Success(true))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((_, _, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
}
