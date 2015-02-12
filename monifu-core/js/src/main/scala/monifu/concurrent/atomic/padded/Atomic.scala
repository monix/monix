/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.concurrent.atomic.padded

trait Atomic[T] extends monifu.concurrent.atomic.Atomic[T]

object Atomic {
  /**
   * Constructs an `Atomic[T]` reference. Based on the `initialValue`, it will return the best, most specific
   * type. E.g. you give it a number, it will return something inheriting from `AtomicNumber[T]`. That's why
   * it takes an `AtomicBuilder[T, R]` as an implicit parameter - but worry not about such details as it just works.
   *
   * @param initialValue is the initial value with which to initialize the Atomic reference
   * @param builder is the builder that helps us to build the best reference possible, based on our `initialValue`
   */
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: PaddedAtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)

  /**
   * Returns the builder that would be chosen to construct Atomic references
   * for the given `initialValue`.
   */
  def builderFor[T, R <: Atomic[T]](initialValue: T)(implicit builder: PaddedAtomicBuilder[T, R]): PaddedAtomicBuilder[T, R] =
    builder
}