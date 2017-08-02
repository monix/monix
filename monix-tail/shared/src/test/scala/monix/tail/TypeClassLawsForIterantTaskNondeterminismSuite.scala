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

package monix.tail

import cats.Monad
import cats.laws.discipline.{CartesianTests, MonadTests, MonoidKTests}
import monix.eval.Task
import monix.eval.Task.nondeterminism

object TypeClassLawsForIterantTaskNondeterminismSuite extends BaseLawsSuite {
  type F[α] = Iterant[Task, α]

  // Explicit instance due to weird implicit resolution problem
  implicit val iso: CartesianTests.Isomorphisms[F] =
    CartesianTests.Isomorphisms.invariant

  checkAllAsync("Monad[Iterant[Task]]") { implicit ec =>
    MonadTests[F].monad[Int, Int, Int]
  }

  checkAllAsync("MonoidK[Iterant[Task]]") { implicit ec =>
    MonoidKTests[F].monoidK[Int]
  }

  test("different instances for nondeterminism and determinism") {
    val ref1 = Monad[F]
    val ref2 = Monad[F]

    val ref3 = {
      import Task.{determinism, nondeterminism => _}
      Monad[F]
    }
    val ref4 = {
      import Task.{determinism, nondeterminism => _}
      Monad[F]
    }

    assert(ref1 == ref2)
    assert(ref2 != ref3)
    assert(ref3 == ref4)
  }
}
