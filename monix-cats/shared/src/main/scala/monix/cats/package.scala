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

/** Package exposing the Monix integration with the Cats library.
  *
  * See the library's documentation at:
  * [[http://typelevel.org/cats/ typelevel.org/cats/]]
  *
  * To convert Monix type-class instances into Cats types:
  *
  * {{{
  *   import monix.cats._
  * }}}
  *
  * To convert Cats type-class instances into Monix types:
  *
  * {{{
  *   import monix.cats.reverse._
  * }}}
  *
  * Do not bring these imports into the same scope as you
  * can experience conflicts if you do:
  *
  * {{{
  *   // Do not do this!
  *   import monix.cats._
  *   import monix.cats.reverse._
  * }}}
  */
package object cats extends MonixToCatsConversions
