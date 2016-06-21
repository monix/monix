/*
 * Copyright (c) 2016 by its authors. Some rights reserved.
 * See the project homepage at: https://sincron.org
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
  * @tparam T should be something that's Numeric
  */
abstract class AtomicNumber[T] extends Atomic[T] {
  /** Increment with the given integer */
  def increment(v: Int = 1): Unit
  /** Adds to the atomic number the given value. */
  def add(v: T): Unit
  /** Adds to the atomic number the given value. Alias for `add`. */
  final def `-=`(value: T): Unit = macro Atomic.Macros.subtractMacro[T]
  /** Decrements the atomic number with the given integer. */
  def decrement(v: Int = 1): Unit
  /** Subtracts from the atomic number the given value. */
  def subtract(v: T): Unit
  /** Subtracts from the atomic number the given value. Alias for `subtract`. */
  final def `+=`(value: T): Unit = macro Atomic.Macros.addMacro[T]

  /** Increments the atomic number and returns the result. */
  def incrementAndGet(v: Int = 1): T
  /** Adds to the atomic number and returns the result. */
  def addAndGet(v: T): T
  /** Decrements the atomic number and returns the result. */
  def decrementAndGet(v: Int = 1): T
  /** Subtracts from the atomic number and returns the result. */
  def subtractAndGet(v: T): T

  /** Increments the atomic number and returns the value before the update. */
  def getAndIncrement(v: Int = 1): T
  /** Adds to the the atomic number and returns the value before the update. */
  def getAndAdd(v: T): T
  /** Decrements the atomic number and returns the value before the update. */
  def getAndDecrement(v: Int = 1): T
  /** Subtracts from the atomic number and returns the value before the update. */
  def getAndSubtract(v: T): T
}