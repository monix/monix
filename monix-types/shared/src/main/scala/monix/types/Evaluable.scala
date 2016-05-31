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

import scala.util.Try

/** Type-class for computations that can be materialized
  * to a single result.
  */
trait Evaluable[F[_]] extends Deferrable[F] with Restartable[F] {
  /** Exposes both successful results and potential errors by
    * in the evaluable context.
    */
  def materialize[A](fa: F[A]): F[Try[A]]

  /** Hides errors in the context that expressed as `Try`. */
  def dematerialize[A](fa: F[Try[A]]): F[A]
}

object Evaluable {
  @inline def apply[F[_]](implicit F: Evaluable[F]) = F
}