/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
 *
 */

package monix.base.atomic

/**
 * Atomic classes that are cache-padded for reducing cache contention,
 * until JEP 142 and `@Contended` happens. See:
 *
 * http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html
 */
package object padded {
  import monix.base.atomic

  // defining useful type-aliases
  type Atomic[T] = atomic.Atomic[T]
  type AtomicAny[T] = atomic.AtomicAny[T]
  type AtomicInt = atomic.AtomicInt
  type AtomicLong = atomic.AtomicLong
  type AtomicBoolean = atomic.AtomicBoolean
  type AtomicByte = atomic.AtomicByte
  type AtomicChar = atomic.AtomicChar
  type AtomicShort = atomic.AtomicShort
  type AtomicDouble = atomic.AtomicDouble
  type AtomicFloat = atomic.AtomicFloat
  type AtomicNumber[T] = atomic.AtomicNumber[T]
  type AtomicNumberAny[T] = atomic.AtomicNumberAny[T]
  type BlockableAtomic[T] = atomic.BlockableAtomic[T]

  /**
   * Constructs an `Atomic[T]` reference. Based on the `initialValue`, it will return the best, most specific
   * type. E.g. you give it a number, it will return something inheriting from `AtomicNumber[T]`. That's why
   * it takes an `AtomicBuilder[T, R]` as an implicit parameter - but worry not about such details as it just works.
   *
   * @param initialValue is the initial value with which to initialize the Atomic reference
   * @param builder is the builder that helps us to build the best reference possible, based on our `initialValue`
   */
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}