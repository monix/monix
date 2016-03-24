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

import cats.Monoid
import cats.laws.{FunctorLaws, IsEq}
import monix.types.FoldableF
import scala.language.higherKinds
import monix.types.implicits._

/** Laws for the [[FoldableF]] type-class.
  *
  * See [[monix.laws.discipline.FoldableFTests FoldableFTests]] for a
  * test specification powered by
  * [[https://github.com/typelevel/discipline/ Discipline]].
  */
trait FoldableFLaws[F[_]] extends FunctorLaws[F] {
  implicit def F: FoldableF[F]

  def leftFoldConsistentWithFoldMapF[A, B](fa: F[A], f: A => B)(implicit M: Monoid[B]): IsEq[F[B]] =
    fa.foldMapF(f) <-> fa.foldLeftF(M.empty) { (b, a) => b |+| f(a) }

  def existsConsistentWithFind[A](fa: F[A], p: A => Boolean): IsEq[F[Boolean]] = {
    fa.existsF(p) <-> fa.findF(p).map(_.isDefined)
  }
}

object FoldableFLaws {
  def apply[F[_] : FoldableF]: FoldableFLaws[F] =
    new FoldableFLaws[F] { def F: FoldableF[F] = implicitly[FoldableF[F]] }
}