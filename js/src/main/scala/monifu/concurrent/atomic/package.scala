/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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
 
package monifu.concurrent

/**
 * A small toolkit of classes that support compare-and-swap semantics for mutation of variables.
 *
 * On top of the JVM, this means dealing with lock-free thread-safe programming.
 * On top of Javascript / Scala.js using `Atomic` references is still good because:
 *
 * 1. boxing values in a smart reference with nice helpers for transformations is always a good idea.
 * 2. on the JVM there are times when synchronization, and when used for synchronization, atomic
 *    references can now cross compile to Scala.js
 * 3. `compareAndSet` is actually a good idea to have even in an asynchronous, non-multi-threaded
 *    environment, such as Javascript, because it takes time into account and time related problems
 *    can happen even without multi-threading
 *
 * The backbone of Atomic references is this method:
 * {{{
 *   def compareAndSet(expect: T, update: T): Boolean
 * }}}
 *
 * This method atomically sets a variable to the `update` value if it currently holds
 * the `expect` value, reporting `true` on success or `false` on failure. The classes in this package
 * also contain methods to get and unconditionally set values. In comparison with the JVM version,
 * these `Atomic` references do not have methods for weakly setting values (i.e. `weakCompareAndSet`, `lazySet`),
 * since those really make no sense in Javascript.
 *
 * Building a reference is easy with the provided constructor, which will automatically return the
 * most specific type needed:
 * {{{
 *   val atomicNumber = Atomic(12L)
 *
 *   atomicNumber.incrementAndGet()
 * }}}
 *
 * In comparison with `java.util.concurrent.AtomicReference`, these references implement common interfaces
 * that you can use generically (i.e. `Atomic[T]`, `AtomicNumber[T]`). And also provide useful helpers for
 * atomically mutating of values (i.e. `transform`, `transformAndGet`, `getAndTransform`, etc...).
 *
 * Other differences with the JVM-variant - in Scala.js you do not have access to the methods meant to
 * block (spin-lock) the current thread (e.g. `waitForCompareAndSet`, `waitForCondition`, etc...), as
 * the semantics of those operations aren't possible on top of Scala.js
 */
package object atomic {
  type AtomicShort = AtomicNumberAny[Short]

  object AtomicShort {
    def apply(initial: Short): AtomicShort  =
      AtomicNumberAny(initial)
  }

  type AtomicChar = AtomicNumberAny[Char]

  object AtomicChar {
    def apply(initial: Char): AtomicChar  =
      AtomicNumberAny(initial)
  }

  type AtomicBoolean = AtomicAny[Boolean]

  object AtomicBoolean {
    def apply(initial: Boolean): AtomicBoolean  =
      AtomicAny(initial)
  }

  type AtomicByte = AtomicNumberAny[Byte]

  object AtomicByte {
    def apply(initial: Byte): AtomicByte  =
      AtomicNumberAny(initial)
  }
}
