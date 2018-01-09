/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution.atomic


/** For applying padding to atomic references, in order to reduce
  * cache contention. JEP 142 should reduce the need for this along
  * with the `@Contended` annotation, however that might have
  * security restrictions, the runtime might not act on it since it's
  * just a recommendation, plus it's nice to provide backwards
  * compatibility.
  *
  * See: [[http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html]]
  *
  * The default strategy is [[PaddingStrategy.NoPadding NoPadding]].
  * In order to apply padding:
  * {{{
  *   import monix.execution.atomic.Atomic
  *   import monix.execution.atomic.PaddingStrategy.Right64
  *
  *   val paddedAtomic = Atomic.withPadding(10, Right64)
  * }}}
  *
  * @see [[PaddingStrategy.NoPadding]]
  * @see [[PaddingStrategy.Left64]]
  * @see [[PaddingStrategy.Right64]]
  * @see [[PaddingStrategy.LeftRight128]]
  * @see [[PaddingStrategy.Left128]]
  * @see [[PaddingStrategy.Right128]]
  * @see [[PaddingStrategy.LeftRight256]]
  */
sealed abstract class PaddingStrategy

object PaddingStrategy {
  /** A [[PaddingStrategy]] that specifies no padding should be applied.
    * This is the default.
    */
  case object NoPadding extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding to the left of our
    * atomic value for a total cache line of 64 bytes (8 longs).
    *
    * Note the actual padding applied will be less, like 48 or 52 bytes,
    * because we take into account the object's header and the
    * the stored value.
    */
  case object Left64 extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding to the right of our
    * atomic value for a total cache line of 64 bytes (8 longs).
    *
    * Note the actual padding applied will be less, like 48 or 52 bytes,
    * because we take into account the object's header and the
    * the stored value.
    */
  case object Right64 extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding both to the left
    * and to the right of our atomic value for a total cache
    * line of 128 bytes (16 longs).
    *
    * Note the actual padding applied will be less, like 112 or 116 bytes,
    * because we take into account the object's header and the stored value.
    */
  case object LeftRight128 extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding to the left of our
    * atomic value for a total cache line of 64 bytes (8 longs).
    *
    * Note the actual padding applied will be less, like 112 bytes,
    * because we take into account the object's header and the
    * the stored value.
    */
  case object Left128 extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding to the right of our
    * atomic value for a total cache line of 64 bytes (8 longs).
    *
    * Note the actual padding applied will be less, like 112 bytes,
    * because we take into account the object's header and the
    * the stored value.
    */
  case object Right128 extends PaddingStrategy

  /** A [[PaddingStrategy]] that applies padding both to the left
    * and to the right of our atomic value for a total cache
    * line of 128 bytes (16 longs).
    *
    * Note the actual padding applied will be less, like 232/240 bytes,
    * because we take into account the object's header and the stored value.
    */
  case object LeftRight256 extends PaddingStrategy
}
