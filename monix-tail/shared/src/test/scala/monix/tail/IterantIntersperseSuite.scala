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
import monix.eval.Coeval
import monix.execution.exceptions.DummyException

object IterantIntersperseSuite extends BaseTestSuite {
  def intersperseList[A](separator: A)(xs: List[A]): List[A] = {
    xs.flatMap(e => e :: separator :: Nil).dropRight(1)
  }

  test("Iterant.intersperse(x) inserts the separator between elem pairs") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], elem: Int) =>
      stream.intersperse(elem).toListL <-> stream.toListL.map(intersperseList(elem))
    }
  }

  test("Iterant.intersperse(x) releases the source's resources") { implicit s =>
    val builders = Iterant[Coeval]
    import builders._

    var effect = 0
    val source = suspendS(
      Coeval(of(1, 2, 3))
    ).guarantee(Coeval(effect += 1))
    val interspersed = source.intersperse(0)
    interspersed.completedL.value()
    assertEquals(effect, 1)
  }

  test("Iterant.intersperse(x) protects against broken batches") { implicit s =>
    val builders = Iterant[Coeval]
    import builders._

    val dummy = DummyException("dummy")
    val stream = of(1, 2, 3) ++ nextBatchS(
      ThrowExceptionBatch(dummy),
      Coeval(empty[Int])
    )

    assertEquals(
      stream.intersperse(0).toListL.runAttempt(),
      Left(dummy)
    )
  }

  test("Iterant.intersperse(x) protects against broken cursors") { implicit s =>
    val builders = Iterant[Coeval]
    import builders._

    val dummy = DummyException("dummy")
    val stream = of(1, 2, 3) ++ nextCursorS(
      ThrowExceptionCursor(dummy),
      Coeval(empty[Int])
    )

    assertEquals(
      stream.intersperse(0).toListL.runAttempt(),
      Left(dummy)
    )
  }

  test("Iterant.intersperse(a, b, c) inserts the separator between elem pairs and adds prefix/suffix") { implicit s =>
    check3 { (stream: Iterant[Coeval, Int], prefix: Int, suffix: Int) =>
      val separator = prefix ^ suffix
      val source = stream.intersperse(prefix, separator, suffix)
      val target = stream.toListL
        .map(intersperseList(separator))
        .map(prefix +: _ :+ suffix)

      source.toListL <-> target
    }
  }
}
