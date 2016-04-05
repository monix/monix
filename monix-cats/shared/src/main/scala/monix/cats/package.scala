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

package monix

import _root_.cats._
import scala.language.higherKinds

/** Package exposing integration with the Cats library.
  *
  * See: [[http://typelevel.org/cats/ typelevel.org/cats/]]
  */
package object cats extends AllInstances {
  type Deferrable[F[_]] = MonadError[F, Throwable] with CoflatMap[F]
  type Evaluable[F[_]] = Deferrable[F] with Bimonad[F]

  type Sequenceable[F[_]] = MonadFilter[F]
    with MonadError[F, Throwable]
    with CoflatMap[F]
    with MonadCombine[F]
}
