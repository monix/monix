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

package monix.reactive

import cats.effect.laws.discipline.BracketTests
import cats.laws.discipline.arbitrary.catsLawsArbitraryForPartialFunction
import cats.laws.discipline.{
  AlternativeTests,
  ApplyTests,
  CoflatMapTests,
  FunctorFilterTests,
  MonoidKTests,
  NonEmptyParallelTests
}
import monix.reactive.observables.CombineObservable

object TypeClassLawsForObservableSuite extends BaseLawsTestSuite {
  checkAllAsync("Bracket[Observable, Throwable]") { implicit ec =>
    BracketTests[Observable, Throwable].bracket[Int, Int, Int]
  }

  checkAllAsync("CoflatMap[Observable]") { implicit ec =>
    CoflatMapTests[Observable].coflatMap[Int, Int, Int]
  }

  checkAllAsync("Alternative[Observable]") { implicit ec =>
    AlternativeTests[Observable].alternative[Int, Int, Int]
  }

  checkAllAsync("MonoidK[Observable]") { implicit ec =>
    MonoidKTests[Observable].monoidK[Int]
  }

  checkAllAsync("Bracket[Observable, Throwable]") { implicit ec =>
    BracketTests[Observable, Throwable].bracket[Int, Int, Int]
  }

  checkAllAsync("Apply[CombineObservable.Type]") { implicit ec =>
    ApplyTests[CombineObservable.Type].apply[Int, Int, Int]
  }

  checkAllAsync("NonEmptyParallel[Observable, CombineObservable.Type]") { implicit ec =>
    NonEmptyParallelTests[Observable].nonEmptyParallel[Int, Int]
  }

  checkAllAsync("FunctorFilter[Observable]") { implicit ec =>
    FunctorFilterTests[Observable].functorFilter[Int, Int, Int]
  }
}
