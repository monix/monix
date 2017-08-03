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

import cats.laws.discipline.{CartesianTests, MonadTests, MonoidKTests}
import monix.eval.Task

object TypeClassLawsForIterantTaskSuite extends BaseLawsSuite {
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
}
