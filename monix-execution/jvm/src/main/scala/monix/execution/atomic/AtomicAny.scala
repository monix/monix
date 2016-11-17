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
import monix.execution.atomic.internals.{Factory, BoxedObject}

/** Atomic references wrapping `AnyRef` values.
  *
  * @tparam T is forced to be an `AnyRef` because the equality test is
  *         by reference and not by value.
  */
final class AtomicAny[T <: AnyRef] private (private[this] val ref: BoxedObject) extends Atomic[T] {
  def get: T = ref.volatileGet().asInstanceOf[T]
  def set(update: T): Unit = ref.volatileSet(update)

  def compareAndSet(expect: T, update: T): Boolean =
    ref.compareAndSet(expect, update)

  def getAndSet(update: T): T =
    ref.getAndSet(update).asInstanceOf[T]

  def lazySet(update: T): Unit =
    ref.lazySet(update)
}

/** @define createDesc Constructs an [[AtomicAny]] reference, allowing
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
object AtomicAny {
  /** Builds an [[AtomicAny]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply[A <: AnyRef](initialValue: A): AtomicAny[A] =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding[A <: AnyRef](initialValue: A, padding: PaddingStrategy): AtomicAny[A] =
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
  def create[A <: AnyRef](initialValue: A, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicAny[A] =
    new AtomicAny(Factory.newBoxedObject(
      initialValue,
      boxStrategyToPaddingStrategy(padding),
      allowPlatformIntrinsics))
}
