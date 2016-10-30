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

/** Groups all syntax extensions. */
trait AllSyntax extends Cobind.Syntax
  with Comonad.Syntax
  with Functor.Syntax
  with Monad.Syntax
  with MonadFilter.Syntax
  with MonadError.Syntax
  with Memoizable.Syntax

/** Provides syntax (extension methods) for usage of [[monix.types]]
  * instances.
  *
  * Usage:
  *
  * {{{
  *   import monix.types.syntax._
  * }}}
  *
  * Do not combine with Cats or Scalaz syntax in
  * the same context.
  */
object syntax extends AllSyntax
