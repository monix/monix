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
import monix.execution.atomic.boxes.{Factory, BoxedInt}
import scala.annotation.tailrec

/** Atomic references wrapping `Byte` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Byte` is a primitive.
  */
final class AtomicByte private (private[this] val ref: BoxedInt)
  extends AtomicNumber[Byte] {

  private[this] val mask = 255

  def get: Byte = (ref.volatileGet() & mask).asInstanceOf[Byte]
  def set(update: Byte): Unit = ref.volatileSet(update)

  def lazySet(update: Byte) = {
    ref.lazySet(update)
  }

  def compareAndSet(expect: Byte, update: Byte): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Byte): Byte = {
    (ref.getAndSet(update) & mask).asInstanceOf[Byte]
  }


  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Byte): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Byte): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Byte): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Byte): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Byte): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Byte): Byte = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Byte]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Byte = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Byte = getAndIncrement(-v)

  private[this] def plusOp(a: Byte, b: Byte): Byte =
    ((a + b) & mask).asInstanceOf[Byte]

  private[this] def minusOp(a: Byte, b: Byte): Byte =
    ((a - b) & mask).asInstanceOf[Byte]

  private[this] def incrementOp(a: Byte, b: Int): Byte =
    ((a + b) & mask).asInstanceOf[Byte]
}

object AtomicByte {
  /** Constructs an [[AtomicByte]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Byte): AtomicByte =
    withPadding(initialValue, NoPadding)

  /** Constructs an [[AtomicByte]] reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing"
    * problem.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it
    * doesn't make much sense, since Javascript execution is single
    * threaded, but this builder is provided for syntax compatibility
    * anyway across the JVM and Javascript and we never know how
    * Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Byte, padding: PaddingStrategy): AtomicByte =
    new AtomicByte(Factory.newBoxedInt(initialValue, boxStrategyToPaddingStrategy(padding)))
}
