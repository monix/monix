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
import monix.execution.atomic.boxes.{BoxedInt, Factory}
import scala.annotation.tailrec

/** Atomic references wrapping `Char` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Char` is a primitive.
  */
final class AtomicChar private (private[this] val ref: BoxedInt)
  extends AtomicNumber[Char] {
  private[this] val mask = 255 + 255 * 256

  def get: Char = (ref.volatileGet() & mask).asInstanceOf[Char]
  def set(update: Char): Unit = ref.volatileSet(update)

  def lazySet(update: Char) = {
    ref.lazySet(update)
  }

  def compareAndSet(expect: Char, update: Char): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Char): Char = {
    (ref.getAndSet(update) & mask).asInstanceOf[Char]
  }


  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  def add(v: Char): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def addAndGet(v: Char): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = incrementOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Char): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = plusOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def subtract(v: Char): Unit = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def subtractAndGet(v: Char): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  def getAndSubtract(v: Char): Char = {
    val current = (ref.volatileGet() & mask).asInstanceOf[Char]
    val update = minusOp(current, v)
    if (!ref.compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Char = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Char = getAndIncrement(-v)

  private[this] def plusOp(a: Char, b: Char): Char =
    ((a + b) & mask).asInstanceOf[Char]

  private[this] def minusOp(a: Char, b: Char): Char =
    ((a - b) & mask).asInstanceOf[Char]

  private[this] def incrementOp(a: Char, b: Int): Char =
    ((a + b) & mask).asInstanceOf[Char]
}

object AtomicChar {
  /** Constructs an [[AtomicChar]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Char): AtomicChar =
    withPadding(initialValue, NoPadding)

  /** Constructs an [[AtomicChar]] reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing" problem.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it doesn't
    * make much sense, since Javascript execution is single threaded, but this
    * builder is provided for syntax compatibility anyway across the JVM and
    * Javascript and we never know how Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    * @param padding is the [[PaddingStrategy]] to apply
    */
  def withPadding(initialValue: Char, padding: PaddingStrategy): AtomicChar =
    new AtomicChar(Factory.newBoxedInt(initialValue, boxStrategyToPaddingStrategy(padding)))
}

