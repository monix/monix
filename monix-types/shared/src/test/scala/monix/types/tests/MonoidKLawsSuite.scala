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

package monix.types.tests

import monix.types.{MonoidK, SemigroupK}
import org.scalacheck.Arbitrary

trait MonoidKLawsSuite[F[_], A] extends SemigroupKLawsSuite[F,A] {
  override def F: MonoidK.Instance[F]

  object monoidKLaws extends MonoidK.Laws[F] {
    implicit def semigroupK: SemigroupK[F] = F
    implicit def monoidK: MonoidK[F] = F
  }

  def monoidKCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryFA: Arbitrary[F[A]],
    eqFA: Eq[F[A]]): Unit = {

    if (includeSupertypes) semigroupKCheck(typeName)

    test(s"MonoidK[$typeName].monoidKLeftIdentity") {
      check1((fa: F[A]) => monoidKLaws.monoidKLeftIdentity(fa))
    }

    test(s"MonoidK[$typeName].monoidKRightIdentity") {
      check1((fa: F[A]) => monoidKLaws.monoidKRightIdentity(fa))
    }
  }
}
