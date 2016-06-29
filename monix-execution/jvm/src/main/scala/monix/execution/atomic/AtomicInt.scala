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

/** Atomic references wrapping `Int` values.
  *
  * Note that the equality test in `compareAndSet` is value based,
  * since `Int` is a primitive.
  */
final class AtomicInt private (private[this] val ref: BoxedInt)
  extends AtomicNumber[Int] {

  def get: Int = ref.volatileGet()
  def set(update: Int): Unit = ref.volatileSet(update)

  def compareAndSet(expect: Int, update: Int): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Int): Int = {
    ref.getAndSet(update)
  }

  def lazySet(update: Int): Unit = {
    ref.lazySet(update)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = ref.volatileGet()
    if (!ref.compareAndSet(current, current+v))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Int = {
    val current = ref.volatileGet()
    val update = current + v
    if (!ref.compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Int = {
    val current = ref.volatileGet()
    val update = current + v
    if (!ref.compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Int): Int = {
    val current = ref.volatileGet()
    val update = current + v
    if (!ref.compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: Int): Int = {
    val current = ref.volatileGet()
    val update = current + v
    if (!ref.compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: Int): Unit = {
    val current = ref.volatileGet()
    val update = current + v
    if (!ref.compareAndSet(current, update))
      add(v)
  }

  def subtract(v: Int): Unit =
    add(-v)

  def getAndSubtract(v: Int): Int =
    getAndAdd(-v)

  def subtractAndGet(v: Int): Int =
    addAndGet(-v)

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Int = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Int = getAndIncrement(-v)

  override def toString: String = s"AtomicInt(${ref.volatileGet()})"
}

object AtomicInt {
  /** Constructs an [[AtomicInt]] reference.
    *
    * @param initialValue is the initial value with which to initialize the atomic
    */
  def apply(initialValue: Int): AtomicInt =
    withPadding(initialValue, NoPadding)

  /** Constructs an [[AtomicInt]] reference, applying the provided
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
  def withPadding(initialValue: Int, padding: PaddingStrategy): AtomicInt =
    new AtomicInt(Factory.newBoxedInt(initialValue, boxStrategyToPaddingStrategy(padding)))
}
