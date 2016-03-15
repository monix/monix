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

package monix.types

import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

/** A type-class for data structures that can be zipped together.
  *
  * An invocation like `zip2(A,B)` will produce pairs such
  * as `(a1,b1), (a2,b2), ...`. This is not the `product` from `Applicative`, as
  * this does not generate a cartesian product, but rather pairs elements based on
  * ordering.
  */
@typeclass trait Zippable[F[_]] {
  def zipList[A](sources: Seq[F[A]]): F[Seq[A]]
  def zipWith2[A1,A2,R](fa1: F[A1], fa2: F[A2])(f: (A1,A2) => R): F[R]

  def zip2[A1,A2](fa1: F[A1], fa2: F[A2]): F[(A1,A2)] =
    zipWith2(fa1,fa2)((a1,a2) => (a1,a2))

  def zip3[A1,A2,A3](fa1: F[A1], fa2: F[A2], fa3: F[A3]): F[(A1,A2,A3)] =
    zipWith3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))

  def zipWith3[A1,A2,A3,R](fa1: F[A1], fa2: F[A2], fa3: F[A3])(f: (A1,A2,A3) => R): F[R] = {
    val fa12 = zip2(fa1, fa2)
    zipWith2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  def zip4[A1,A2,A3,A4](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4]): F[(A1,A2,A3,A4)] =
    zipWith4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))

  def zipWith4[A1,A2,A3,A4,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4])(f: (A1,A2,A3,A4) => R): F[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipWith2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  def zip5[A1,A2,A3,A4,A5](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5]): F[(A1,A2,A3,A4,A5)] =
    zipWith5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  def zipWith5[A1,A2,A3,A4,A5,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5])(f: (A1,A2,A3,A4,A5) => R): F[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipWith2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  def zip6[A1,A2,A3,A4,A5,A6](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6]): F[(A1,A2,A3,A4,A5,A6)] =
    zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  def zipWith6[A1,A2,A3,A4,A5,A6,R](fa1: F[A1], fa2: F[A2], fa3: F[A3], fa4: F[A4], fa5: F[A5], fa6: F[A6])(f: (A1,A2,A3,A4,A5,A6) => R): F[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipWith2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }
}
