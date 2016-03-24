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

import cats.laws.{BimonadLaws, IsEq}
import monix.types.Evaluable
import scala.language.higherKinds

/** Laws for the [[Evaluable]] type-class.
  *
  * See [[monix.laws.discipline.EvaluableTests EvaluableTests]] for a
  * test specification powered by
  * [[https://github.com/typelevel/discipline/ Discipline]].
  */
trait EvaluableLaws[F[_]] extends NonstrictLaws[F] with BimonadLaws[F] {
  implicit def F: Evaluable[F]

  def lazyNowCanEvaluate[A](a: A): IsEq[A] =
    a <-> F.value(F.now(a))

  def lazyEvalAlwaysCanEvaluate[A](a: A): IsEq[A] =
    a <-> F.value(F.evalAlways(a))

  def lazyEvalOnceCanEvaluate[A](a: A): IsEq[A] =
    a <-> F.value(F.evalOnce(a))

  def lazyForNowExtractIsAliasOfValue[A](a: A): IsEq[A] =
    F.value(F.now(a)) <-> F.extract(F.now(a))

  def lazyForAlwaysExtractIsAliasOfValue[A](a: A): IsEq[A] =
    F.value(F.evalAlways(a)) <-> F.extract(F.evalAlways(a))

  def lazyForOnceExtractIsAliasOfValue[A](a: A): IsEq[A] =
    F.value(F.evalOnce(a)) <-> F.extract(F.evalOnce(a))
}

object EvaluableLaws {
  def apply[F[_] : Evaluable]: EvaluableLaws[F] =
    new EvaluableLaws[F] { def F: Evaluable[F] = implicitly[Evaluable[F]] }
}