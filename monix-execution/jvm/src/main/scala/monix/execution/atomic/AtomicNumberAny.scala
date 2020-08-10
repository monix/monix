/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.atomic.{BoxedObject, Factory}
import scala.annotation.tailrec

/** Atomic references wrapping any values implementing
  * Scala's `Numeric` type class.
  *
  * Note that the equality test in `compareAndSet` is reference based.
  * This is because we are storing `AnyRef` references and on top
  * of the JVM that's the semantic of `compareAndSet`. This behavior
  * is kept consistent even on top of Scala.js / Javascript.
  */
final class AtomicNumberAny[A <: AnyRef: Numeric] private (private[this] val ref: BoxedObject) extends AtomicNumber[A] {

  private[this] val ev = implicitly[Numeric[A]]

  def get(): A = ref.volatileGet().asInstanceOf[A]
  def set(update: A): Unit = ref.volatileSet(update)

  def compareAndSet(expect: A, update: A): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: A): A = {
    ref.getAndSet(update).asInstanceOf[A]
  }

  def lazySet(update: A): Unit = {
    ref.lazySet(update)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = get()
    if (!compareAndSet(current, ev.plus(current, ev.fromInt(v))))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): A = {
    val current = get()
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): A = {
    val current = get()
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: A): A = {
    val current = get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: A): A = {
    val current = get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: A): Unit = {
    val current = get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def subtract(v: A): Unit = {
    val current = get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def getAndSubtract(v: A): A = {
    val current = get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @tailrec
  def subtractAndGet(v: A): A = {
    val current = get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): A = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): A = getAndIncrement(-v)
}

/** @define createDesc Constructs an [[AtomicNumberAny]] reference, allowing
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
object AtomicNumberAny {
  /** Constructs an [[AtomicNumberAny]] reference.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    */
  def apply[A <: AnyRef: Numeric](initialValue: A): AtomicNumberAny[A] =
    withPadding(initialValue, NoPadding)

  /** $createDesc
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding[A <: AnyRef: Numeric](initialValue: A, padding: PaddingStrategy): AtomicNumberAny[A] =
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
  def create[A <: AnyRef: Numeric](
    initialValue: A,
    padding: PaddingStrategy,
    allowPlatformIntrinsics: Boolean): AtomicNumberAny[A] = {
    new AtomicNumberAny[A](
      Factory.newBoxedObject(
        initialValue,
        boxStrategyToPaddingStrategy(padding),
        true, // allowUnsafe
        allowPlatformIntrinsics
      ))
  }

  /** $createDesc
    *
    * This builder guarantees to construct a safe atomic reference that
    * does not make use of `sun.misc.Unsafe`. On top of platforms that
    * don't support it, notably some versions of Android or on top of
    * the upcoming Java 9, this might be desirable.
    *
    * NOTE that explicit usage of this builder is not usually necessary
    * because [[create]] can auto-detect whether the underlying platform
    * supports `sun.misc.Unsafe` and if it does, then its usage is
    * recommended, because the "safe" atomic instances have overhead.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def safe[A <: AnyRef: Numeric](initialValue: A, padding: PaddingStrategy): AtomicNumberAny[A] =
    new AtomicNumberAny[A](
      Factory.newBoxedObject(
        initialValue,
        boxStrategyToPaddingStrategy(padding),
        false, // allowUnsafe
        false // allowPlatformIntrinsics
      ))
}
