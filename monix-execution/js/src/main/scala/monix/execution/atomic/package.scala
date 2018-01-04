/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution

/** A small toolkit of classes that support compare-and-swap semantics for safe mutation of variables.
  *
  * On top of the JVM, this means dealing with lock-free thread-safe programming. Also works on top of Javascript,
  * with Scala.js, for API compatibility purposes and because it's a useful way to box a value.
  *
  * The backbone of Atomic references is this method:
  * {{{
  *   def compareAndSet(expect: T, update: T): Boolean
  * }}}
  *
  * This method atomically sets a variable to the `update` value if it currently holds
  * the `expect` value, reporting `true` on success or `false` on failure. The classes in this package
  * also contain methods to get and unconditionally set values.
  *
  * Building a reference is easy with the provided constructor, which will automatically
  * return the most specific type needed (in the following sample, that's an `AtomicDouble`,
  * inheriting from `AtomicNumber[A]`):
  * {{{
  *   val atomicNumber = Atomic(12.2)
  *
  *   atomicNumber.incrementAndGet()
  *   // => 13.2
  * }}}
  *
  * These also provide useful helpers for atomically mutating of values
  * (i.e. `transform`, `transformAndGet`, `getAndTransform`, etc...) or of numbers of any kind
  * (`incrementAndGet`, `getAndAdd`, etc...).
  */
package object atomic