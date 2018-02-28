/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import cats.Eq
import cats.data.EitherT
import cats.effect.laws.discipline.AsyncTests
import cats.laws.discipline.{CoflatMapTests, MonadErrorTests, MonoidKTests, SemigroupalTests}
import monix.eval.Task

object TypeClassLawsForIterantTaskSuite extends BaseLawsSuite {
  type F[α] = Iterant[Task, α]

  // Explicit instance due to weird implicit resolution problem
  implicit val iso: SemigroupalTests.Isomorphisms[F] =
    SemigroupalTests.Isomorphisms.invariant

  // Explicit instance, since Scala can't figure it out below :-(
  val eqEitherT: Eq[EitherT[F, Throwable, Int]] =
    implicitly[Eq[EitherT[F, Throwable, Int]]]

  checkAllAsync("Async[Iterant[Task]]", slowConfig) { implicit ec =>
    if (System.getenv("TRAVIS") == "true") {
      ignore("Travis is too slow for this test")
    }

    implicit val eqE = eqEitherT
    AsyncTests[F].async[Int, Int, Int]
  }

  checkAllAsync("MonadError[Iterant[Task]]") { implicit ec =>
    implicit val eqE = eqEitherT
    MonadErrorTests[F, Throwable].monadError[Int, Int, Int]
  }

  checkAllAsync("MonoidK[Iterant[Task]]") { implicit ec =>
    MonoidKTests[F].monoidK[Int]
  }

  checkAllAsync("CoflatMap[Iterant[IO]]") { implicit ec =>
    CoflatMapTests[F].coflatMap[Int, Int, Int]
  }
}
