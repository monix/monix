/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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
 
package monifu.concurrent.atomic

import java.util.concurrent.atomic.{AtomicInteger => JavaAtomicInteger}
import scala.annotation.tailrec

final class AtomicInt private (ref: JavaAtomicInteger)
  extends AtomicNumber[Int] {

  def get: Int = ref.get()

  def set(update: Int): Unit = {
    ref.set(update)
  }

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
  def transformAndExtract[U](cb: (Int) => (U, Int)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Int) => Int): Int = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Int) => Int): Int = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Int) => Int): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = ref.get()
    if (!compareAndSet(current, current+v))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): Int = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): Int = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: Int): Int = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: Int): Int = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: Int): Unit = {
    val current = ref.get()
    val update = current + v
    if (!compareAndSet(current, update))
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

  override def toString: String =
    s"AtomicInt(${ref.get()})"
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt =
    new AtomicInt(new JavaAtomicInteger(initialValue))

  def wrap(ref: JavaAtomicInteger): AtomicInt =
    new AtomicInt(ref)
}
