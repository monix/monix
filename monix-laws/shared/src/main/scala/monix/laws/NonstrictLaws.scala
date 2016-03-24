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

package monix.laws

import cats.laws.{CoflatMapLaws, IsEq, MonadLaws}
import monix.types.Nonstrict
import scala.language.higherKinds

/** Laws for the [[monix.types.Nonstrict Nonstrict]] type-class.
  *
  * See [[monix.laws.discipline.NonstrictTests NonstrictTests]] for a
  * test specification powered by
  * [[https://github.com/typelevel/discipline/ Discipline]].
  */
trait NonstrictLaws[F[_]] extends MonadLaws[F] with CoflatMapLaws[F] {
  implicit def F: Nonstrict[F]

  def evaluableAlwaysEquivalence[A](a: A): IsEq[F[A]] =
    F.now(a) <-> F.evalAlways(a)

  def evaluableOnceEquivalence[A](a: A): IsEq[F[A]] =
    F.now(a) <-> F.evalOnce(a)

  def evaluableDeferEquivalence[A](a: A): IsEq[F[A]] =
    F.now(a) <-> F.defer(F.now(a))

  def evaluableMemoizeEquivalence[A](fa: F[A]): IsEq[F[A]] =
    fa <-> F.memoize(fa)
}

object NonstrictLaws {
  def apply[F[_] : Nonstrict]: NonstrictLaws[F] =
    new NonstrictLaws[F] { def F: Nonstrict[F] = implicitly[Nonstrict[F]] }
}
