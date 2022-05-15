/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.execution

import monix.execution.Features.{ Flag, Flags }

/** `Features` describes a set of features described via
  * bitwise operators applied to ints, but made type safe.
  *
  * This is like a special purpose `BitSet`.
  *
  * Currently used to describe the features of [[Scheduler]].
  */
final class Features(val flags: Flags) extends AnyVal with Serializable {
  /**
    * Longersects the source with another feature set, such
    * that the new value will contain the flags that are
    * contained by both.
    */
  def intersect(other: Features): Features =
    new Features((flags & other.flags).asInstanceOf[Flags])

  /** Computes the union between the source and another
    * feature set, such that the new value contains the
    * features of both.
    */
  def union(other: Features): Features =
    new Features((flags | other.flags).asInstanceOf[Flags])

  /** Computes the difference between the source and
    * another feature set, such that the new value contains
    * the features of the source that are not in the other set.
    */
  def diff(other: Features): Features =
    new Features((flags & (~other.flags)).asInstanceOf[Flags])

  /** Adds a feature to the set. */
  def +(feature: Flag): Features =
    new Features((flags | feature).asInstanceOf[Flags])

  /** Tests if a given feature is in the set. */
  def contains(feature: Flag): Boolean =
    (flags & feature) != (0L).asInstanceOf[Flag]

  override def toString: String =
    s"Features($flags)"
}

/**
  * @define newtypeEncoding Encoding for the newtype we're defining,
  *         used to make [[Features.Flag Flag]] and
  *         [[Features.Flags Flags]] type safe (instead of usage of
  *         `Long`), but without the boxing.
  *
  *         Inspired by the
  *         [[https://github.com/alexknvl/newtypes alexknvl/newtypes]]
  *         project.
  */
object Features {
  /** $newtypeEncoding */
  trait FlagTag extends Any

  /** $newtypeEncoding */
  trait FlagsTag extends Any

  /** Encodes a feature flag, stored as a `Long`.
    *
    * Note that feature flags should be powers of 2, because they'll
    * get added to the set via bitwise arithmetic.
    *
    * Example:
    * {{{
    *   val RED   = Features.flag(1)
    *   val GREEN = Features.flag(2)
    *   val BLUE  = Features.flag(4)
    *   val WHITE = Features.flag(8)
    * }}}
    *
    * You can wrap a `Long` into a `Flag` via [[flag]].
    */
  type Flag = monix.execution.compat.Features.Flag

  /** Encodes a set of [[Flag]] values.
    *
    * Internally this is still a `Long`, but has its own type for
    * type safety reasons.
    */
  type Flags = monix.execution.compat.Features.Flags

  /** Reusable, empty [[Features]] reference. */
  val empty = Features()

  /**
    * Builds a new [[Features]] instance.
    *
    * @param flags is a list of feature flags to instantiate the
    *        value with.
    */
  def apply(flags: Flag*): Features = {
    var set: Long = 0
    for (f <- flags) set = set | f
    new Features(set.asInstanceOf[Flags])
  }

  /** Wraps a `Long` value into a [[Flag]]. */
  def flag(value: Long): Flag =
    value.asInstanceOf[Flag]
}
