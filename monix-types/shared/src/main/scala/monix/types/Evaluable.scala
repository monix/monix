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

import cats.Bimonad
import simulacrum.typeclass
import scala.language.{higherKinds, implicitConversions}

/** Type-class for representing values with non-strict semantics.
  *
  * This type implies synchronous execution.
  *
  * Must obey the laws defined in `monix.laws.EvaluableLaws`.
  */
@typeclass trait Evaluable[F[_]] extends Nonstrict[F] with Bimonad[F] {
  /** Evaluate the computation and return an A value. */
  def value[A](fa: F[A]): A

  // From Comonad
  final override def extract[A](fa: F[A]): A = value(fa)
}
