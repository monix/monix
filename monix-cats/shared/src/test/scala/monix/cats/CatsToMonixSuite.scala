/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.cats

import cats.Eval
import monix.types._
import monix.types.syntax._

object CatsToMonixSuite extends BaseLawsSuite with CatsToMonixConversions {
  test("functor") {
    def test[F[_] : Functor](x: F[Int]): F[Int] =
      x.map(_ + 1)

    assertEquals(test(Eval.always(1)).value, 2)
  }

  test("applicative") {
    def test[F[_]](x: F[Int => Int])(implicit F: Applicative[F]): F[Int] =
      x.ap(1.pure)

    assertEquals(test(Eval.always((x: Int) => x + 1)).value, 2)
  }

  test("recoverable") {
    def test[F[_]](x: F[Int])(implicit F: Recoverable[F,Throwable]): F[Int] =
      x.onErrorHandle(_ => 2)

    val ref = Eval.always[Int](throw new RuntimeException)
    assertEquals(test(ref).value, 2)
  }
}
