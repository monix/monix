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

import monix.types.shims.{CoflatMap, MonadError, MonadPlus}
import simulacrum.typeclass

/** Groups common type-classes for things that represent
  * (possibly asynchronous) streams (e.g. `Observable`).
  */
@typeclass(excludeParents = List("MonadPlus", "CoflatMap"))
trait Streamable[F[_]] extends Deferrable[F]
  with MonadError[F, Throwable]
  with MonadPlus[F] with CoflatMap[F]
