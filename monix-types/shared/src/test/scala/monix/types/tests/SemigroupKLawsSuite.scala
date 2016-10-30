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

import monix.types.SemigroupK
import org.scalacheck.Arbitrary

trait SemigroupKLawsSuite[F[_], A] extends BaseLawsSuite {
  def F: SemigroupK.Instance[F]

  object semigroupKLaws extends SemigroupK.Laws[F] {
    implicit def semigroupK: SemigroupK[F] = F
  }

  def semigroupKCheck(typeName: String)(implicit
    arbitraryFA: Arbitrary[F[A]],
    eqFA: Eq[F[A]]): Unit = {

    test(s"SemigroupK[$typeName].semigroupKAssociative") {
      check3((fa: F[A], fb: F[A], fc: F[A]) =>
        semigroupKLaws.semigroupKAssociative(fa, fb, fc))
    }
  }
}
