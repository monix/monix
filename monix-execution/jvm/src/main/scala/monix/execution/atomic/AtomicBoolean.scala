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

import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.internals.{Factory, BoxedInt}

/** Atomic references wrapping `Boolean` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Boolean` is a primitive.
  */
final class AtomicBoolean private (private[this] val ref: BoxedInt) extends Atomic[Boolean] {
  def get: Boolean =
    ref.volatileGet() == 1

  def set(update: Boolean): Unit =
    ref.volatileSet(if (update) 1 else 0)

  def compareAndSet(expect: Boolean, update: Boolean): Boolean =
    ref.compareAndSet(if (expect) 1 else 0, if (update) 1 else 0)

  def getAndSet(update: Boolean): Boolean =
    ref.getAndSet(if (update) 1 else 0) == 1

  def lazySet(update: Boolean): Unit =
    ref.lazySet(if (update) 1 else 0)

  override def toString: String =
    s"AtomicBoolean($get)"
}

/** @define createDesc Constructs an [[AtomicBoolean]] reference, allowing
  *         for fine-tuning of the created instance.
  *
  *         A [[PaddingStrategy]] can be provided in order to counter
  *         the "false sharing" problem.
  *
  *         Note that for ''Scala.js'' we aren't applying any padding,
  *         as it doesn't make much sense, since Javascript execution
  *         is single threaded, but this builder is provided for
  *         syntax compatibility anyway across the JVM and Javascript
  *         and we never know how Javascript engines will evolve.
  */
object AtomicBoolean {
  /** Builds an [[AtomicBoolean]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Boolean): AtomicBoolean =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Boolean, padding: PaddingStrategy): AtomicBoolean =
    create(initialValue, padding, allowPlatformIntrinsics = true)

  /** $createDesc
    *
    * Also this builder on top Java 8 also allows for turning off the
    * Java 8 intrinsics, thus forcing usage of CAS-loops for
    * `getAndSet` and for `getAndAdd`.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    * @param allowPlatformIntrinsics is a boolean parameter that specifies whether
    *        the instance is allowed to use the Java 8 optimized operations
    *        for `getAndSet` and for `getAndAdd`
    */
  def create(initialValue: Boolean, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicBoolean =
    new AtomicBoolean(Factory.newBoxedInt(
      if (initialValue) 1 else 0,
      boxStrategyToPaddingStrategy(padding),
      allowPlatformIntrinsics))
}
