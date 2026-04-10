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

import cats.Eq
import cats.data.EitherT
import cats.laws.discipline.{
  CoflatMapTests,
  DeferTests,
  FunctorFilterTests,
  MonadErrorTests,
  MonoidKTests,
  SemigroupalTests
}
import cats.laws.discipline.arbitrary.catsLawsArbitraryForPartialFunction
import monix.eval.Task
import monix.execution.schedulers.TestScheduler

object TypeClassLawsForIterantTaskSuite extends BaseLawsSuite {
  type F[α] = Iterant[Task, α]

  implicit lazy val ec: TestScheduler = TestScheduler()

  // Explicit instance due to weird implicit resolution problem
  implicit lazy val iso: SemigroupalTests.Isomorphisms[F] =
    SemigroupalTests.Isomorphisms.invariant

  // Explicit instance, since Scala can't figure it out below :-(
  val eqEitherT: Eq[EitherT[F, Throwable, Int]] =
    implicitly[Eq[EitherT[F, Throwable, Int]]]

  checkAllAsync("Defer[Iterant[Task]]", slowConfig) { _ =>
    DeferTests[F].defer[Int]
  }

  checkAllAsync("MonadError[Iterant[Task]]") { _ =>
    implicit val eqE = eqEitherT
    MonadErrorTests[F, Throwable].monadError[Int, Int, Int]
  }

  checkAllAsync("MonoidK[Iterant[Task]]") { implicit ec =>
    MonoidKTests[F].monoidK[Int]
  }

  checkAllAsync("CoflatMap[Iterant[Task]]") { implicit ec =>
    CoflatMapTests[F].coflatMap[Int, Int, Int]
  }

  checkAllAsync("FunctorFilter[Iterant[Task]]") { implicit ec =>
    FunctorFilterTests[F].functorFilter[Int, Int, Int]
  }
}
