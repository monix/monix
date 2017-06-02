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

package monix.scalaz.tests

import minitest.SimpleTestSuite
import monix.types._
import monix.types.syntax._
import monix.scalaz.reverse._
import scalaz.Need

object ScalazToMonixSuite extends SimpleTestSuite with scalaz.std.AllInstances {
  test("functor") {
    def test[F[_]](x: F[Int])(implicit F: Functor[F]): F[Int] =
      x.map(_ + 1)

    assertEquals(test(Need(1)).extract, 2)
  }

  test("applicative") {
    def test[F[_]](x: F[Int => Int])(implicit F: Applicative[F]): F[Int] =
      F.ap(x)(F.pure(1))

    assertEquals(test(Need((x: Int) => x + 1)).extract, 2)
  }

  test("monad") {
    def test[F[_]](x: F[Int])(implicit M: Monad[F], A: Applicative[F]): F[Int] =
      x.flatMap(r => A.pure(r + 1))

    assertEquals(test(Need(1)).extract, 2)
  }

  test("coflatMap") {
    def test[F[_]](x: F[Int])(implicit F: Cobind[F]): F[Int] =
      x.coflatMap(_ => 2)

    assertEquals(test(Need(1)).extract, 2)
  }

  test("comonad") {
    def test[F[_]](x: F[Int])(implicit F: Comonad[F]): Int =
      x.extract

    assertEquals(test(Need(1)), 1)
  }

  test("monadFilter") {
    def test[F[_]](x: F[Int])(implicit M: MonadFilter[F]): F[Int] =
      x.filter(_ % 2 == 0)

    val list = (0 until 100).toList
    assertEquals(test(list).sum, list.filter(_ % 2 == 0).sum)
  }

  test("semigroupK") {
    val ev = implicitly[SemigroupK[List]]
    assert(ev != null)
  }

  test("monoidK") {
    val ev = implicitly[MonoidK[List]]
    assert(ev != null)
  }

  test("monadRec") {
    val ev = implicitly[MonadRec[List]]
    assert(ev != null)
  }
}
