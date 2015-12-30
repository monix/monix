/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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
 
package monix.concurrent.atomic

import java.util.concurrent.atomic.{AtomicReference => JavaAtomicReference}
import scala.annotation.tailrec

final class AtomicNumberAny[T : Numeric] private (ref: JavaAtomicReference[T])
  extends AtomicNumber[T] {
  
  private[this] val ev = implicitly[Numeric[T]]

  def get: T = ref.get()

  def set(update: T): Unit = {
    ref.set(update)
  }

  def compareAndSet(expect: T, update: T): Boolean = {
    val current = ref.get()
    current == expect && ref.compareAndSet(current, update)
  }

  def getAndSet(update: T): T = {
    ref.getAndSet(update)
  }

  def lazySet(update: T): Unit = {
    ref.lazySet(update)
  }

  @tailrec
  def transformAndExtract[U](cb: (T) => (U, T)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (T) => T): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  def increment(v: Int = 1): Unit = {
    val current = ref.get()
    if (!compareAndSet(current, ev.plus(current, ev.fromInt(v))))
      increment(v)
  }

  @tailrec
  def incrementAndGet(v: Int = 1): T = {
    val current = ref.get()
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  def getAndIncrement(v: Int = 1): T = {
    val current = ref.get()
    val update = ev.plus(current, ev.fromInt(v))
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  def getAndAdd(v: T): T = {
    val current = ref.get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  def addAndGet(v: T): T = {
    val current = ref.get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  def add(v: T): Unit = {
    val current = ref.get()
    val update = ev.plus(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  def subtract(v: T): Unit = {
    val current = ref.get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  def getAndSubtract(v: T): T = {
    val current = ref.get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  @tailrec
  def subtractAndGet(v: T): T = {
    val current = ref.get()
    val update = ev.minus(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): T = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): T = getAndIncrement(-v)
}

object AtomicNumberAny {
  def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] =
    new AtomicNumberAny(new JavaAtomicReference[T](initialValue))

  def wrap[T : Numeric](ref: JavaAtomicReference[T]): AtomicNumberAny[T] =
    new AtomicNumberAny[T](ref)
}
