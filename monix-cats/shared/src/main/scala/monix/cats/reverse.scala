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

package monix.cats

/** Defines conversions from [[http://typelevel.org/cats/ Cats]]
  * type-class instances to the Monix type-classes defined in
  * [[monix.types]].
  *
  * To use:
  *
  * {{{
  *   import monix.cats.reverse._
  * }}}
  *
  * Note that importing both this and the Monix to Cats
  * conversions in the same scope can create conflicts:
  *
  * {{{
  *   // Don't do this!
  *   import monix.cats._
  *   import monix.cats.reverse._
  * }}}
  */
object reverse extends CatsToMonixConversions