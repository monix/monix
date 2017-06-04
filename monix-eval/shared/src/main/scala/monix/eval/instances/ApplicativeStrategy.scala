/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval.instances

/** An enum defining the applicative strategy to use when applying
  * `Applicative.map2` and `Applicative.ap`.
  * 
  * This is relevant for data types that can do parallel processing
  * with unordered side effects, like `Task`.
  * 
  * The default is [[ApplicativeStrategy.Sequential Sequential]]
  * processing.
  */
sealed trait ApplicativeStrategy[+F[_]]

object ApplicativeStrategy {
  /** Returns the [[Sequential]] strategy, parametrized by the given `F` type. */
  @inline def sequential[F[_]]: ApplicativeStrategy[F] =
    Sequential

  /** Returns the [[Parallel]] strategy, parametrized by the given `F` type. */
  @inline def parallel[F[_]]: ApplicativeStrategy[F] =
    Parallel

  /** An [[ApplicativeStrategy]] specifying that sequential processing
    * should be used when applying operations such as `map2` or `ap` 
    * (e.g. ordered results, ordered side effects).
    */
  case object Sequential extends ApplicativeStrategy[Nothing]

  /** An [[ApplicativeStrategy]] specifying that parallel processing
    * should be used when applying operations such as `map2` or `ap`
    * (e.g. ordered results, but unordered side effects).
    */
  case object Parallel extends ApplicativeStrategy[Nothing]
}
