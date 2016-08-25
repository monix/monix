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

/** Groups common type-classes for things that can be evaluated
  * and that yield a single result (i.e. `Task`, `Coeval`)
  */
trait Evaluable[F[_]]
  extends Suspendable[F]
  with Memoizable[F]
  with MonadError[F, Throwable]
  with CoflatMap[F]
  with MonadRec[F]

object Evaluable {
  @inline def apply[F[_]](implicit F: Evaluable[F]): Evaluable[F] = F
}
