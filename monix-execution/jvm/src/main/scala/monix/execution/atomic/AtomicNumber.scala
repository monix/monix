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

import scala.language.experimental.macros

/** Represents an Atomic reference holding a number, providing helpers
  * for easily incrementing and decrementing it.
  *
  * @tparam A should be something that's Numeric
  */
abstract class AtomicNumber[A] extends Atomic[A] {
  /** Increment with the given integer */
  def increment(v: Int = 1): Unit
  /** Adds to the atomic number the given value. */
  def add(v: A): Unit
  /** Adds to the atomic number the given value. Alias for `add`. */
  final def `-=`(value: A): Unit = macro Atomic.Macros.subtractMacro[A]
  /** Decrements the atomic number with the given integer. */
  def decrement(v: Int = 1): Unit
  /** Subtracts from the atomic number the given value. */
  def subtract(v: A): Unit
  /** Subtracts from the atomic number the given value. Alias for `subtract`. */
  final def `+=`(value: A): Unit = macro Atomic.Macros.addMacro[A]

  /** Increments the atomic number and returns the result. */
  def incrementAndGet(v: Int = 1): A
  /** Adds to the atomic number and returns the result. */
  def addAndGet(v: A): A
  /** Decrements the atomic number and returns the result. */
  def decrementAndGet(v: Int = 1): A
  /** Subtracts from the atomic number and returns the result. */
  def subtractAndGet(v: A): A

  /** Increments the atomic number and returns the value before the update. */
  def getAndIncrement(v: Int = 1): A
  /** Adds to the the atomic number and returns the value before the update. */
  def getAndAdd(v: A): A
  /** Decrements the atomic number and returns the value before the update. */
  def getAndDecrement(v: Int = 1): A
  /** Subtracts from the atomic number and returns the value before the update. */
  def getAndSubtract(v: A): A
}