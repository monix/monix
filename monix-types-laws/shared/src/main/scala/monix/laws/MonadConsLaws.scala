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

import cats.laws.{IsEq, MonadFilterLaws}
import monix.types.MonadCons
import scala.language.higherKinds

trait MonadConsLaws[F[_]] extends MonadFilterLaws[F] with EvaluableLaws[F] {
  implicit def F: MonadCons[F]

  def monadConsFromListIsConsistentWithCons[A](list: List[A]): IsEq[F[A]] = {
    val fa = list.reverse.foldLeft(F.empty[A])((acc,e) => F.cons(e, acc))
    fa <-> F.fromList(list)
  }

  def monadConsFlatMapIsAliased[A,B](fa: F[A], f: A => F[B]): IsEq[F[B]] =
    F.flatMap(fa)(f) <-> F.concatMap(fa)(f)

  def monadConsFlattenIsAliased[A,B](fa: F[A], f: A => F[B]): IsEq[F[B]] =
    F.flatten(F.map(fa)(f)) <-> F.concat(F.map(fa)(f))

  def monadConsCollectIsConsistentWithFilter[A,B](fa: F[A], p: A => Boolean, f: A => B): IsEq[F[B]] = {
    val pf: PartialFunction[A, B] = { case a if p(a) => f(a) }
    F.collect(fa)(pf) <-> F.map(F.filter(fa)(pf.isDefinedAt))(a => pf(a))
  }

  def monadConsFollowWithIsConsistentWithCons[A](f1: F[A], f2: F[A]): IsEq[F[A]] =
    F.followWith(f1, f2) <-> F.flatten(F.cons(f1, F.pure(f2)))

  def monadConsFollowWithIsTransitive[A](f1: F[A], f2: F[A], f3: F[A]): IsEq[F[A]] =
    F.followWith(F.followWith(f1, f2), f3) <-> F.followWith(f1, F.followWith(f2, f3))

  def monadConsFollowWithEmptyMirrorsSource[A](fa: F[A]): IsEq[F[A]] =
    fa <-> F.followWith(fa, F.empty[A])

  def monadConsEmptyFollowWithMirrorsSource[A](fa: F[A]): IsEq[F[A]] =
    fa <-> F.followWith(F.empty[A], fa)

  def monadConsEndWithElemConsistentWithFollowWith[A](fa: F[A], a: A): IsEq[F[A]] =
    F.endWithElem(fa, a) <-> F.followWith(fa, F.pure(a))

  def monadConsStartWithElemConsistentWithFollowWith[A](fa: F[A], a: A): IsEq[F[A]] =
    F.startWithElem(fa, a) <-> F.followWith(F.pure(a), fa)
}

object MonadConsLaws {
  def apply[F[_] : MonadCons]: MonadConsLaws[F] =
    new MonadConsLaws[F] { def F: MonadCons[F] = implicitly[MonadCons[F]] }
}
