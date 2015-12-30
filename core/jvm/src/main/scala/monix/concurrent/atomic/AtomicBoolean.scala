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

import java.util.concurrent.atomic.{AtomicBoolean => JavaAtomicBoolean}
import scala.annotation.tailrec

final class AtomicBoolean private (ref: JavaAtomicBoolean) extends Atomic[Boolean] {
  def get: Boolean = {
    ref.get()
  }

  def set(update: Boolean): Unit = {
    ref.set(update)
  }

  def compareAndSet(expect: Boolean, update: Boolean): Boolean = {
    ref.compareAndSet(expect, update)
  }

  def getAndSet(update: Boolean): Boolean = {
    ref.getAndSet(update)
  }

  def lazySet(update: Boolean): Unit = {
    ref.lazySet(update)
  }

  @tailrec
  def transformAndExtract[U](cb: (Boolean) => (U, Boolean)): U = {
    val current = get
    val (extract, update) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  def transformAndGet(cb: (Boolean) => Boolean): Boolean = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  def getAndTransform(cb: (Boolean) => Boolean): Boolean = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  def transform(cb: (Boolean) => Boolean): Unit = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  override def toString: String =
    s"AtomicBoolean(${ref.get})"
}

object AtomicBoolean {
  def apply(initialValue: Boolean): AtomicBoolean =
    new AtomicBoolean(new JavaAtomicBoolean(initialValue))

  def wrap(ref: JavaAtomicBoolean): AtomicBoolean =
    new AtomicBoolean(ref)
}
