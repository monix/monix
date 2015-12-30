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
 
package monix.concurrent

import scala.concurrent.TimeoutException

/**
 * A small toolkit of classes that support compare-and-swap semantics for safe mutation of variables.
 *
 * On top of the JVM, this means dealing with lock-free thread-safe programming. Also works on top of Javascript,
 * with Scala.js (for good reasons, as Atomic references are still useful in non-multi-threaded environments).
 *
 * The backbone of Atomic references is this method:
 * {{{
 *   def compareAndSet(expect: T, update: T): Boolean
 * }}}
 *
 * This method atomically sets a variable to the `update` value if it currently holds
 * the `expect` value, reporting `true` on success or `false` on failure. The classes in this package
 * also contain methods to get and unconditionally set values. They also support weak operations,
 * defined in `WeakAtomic[T]`, such as (e.g. `weakCompareAndSet`, `lazySet`) or operations that
 * block the current thread through ''spin-locking'', until a condition happens (e.g. `waitForCompareAndSet`),
 * methods exposed by `BlockingAtomic[T]`.
 *
 * Building a reference is easy with the provided constructor, which will automatically return the
 * most specific type needed (in the following sample, that's an `AtomicDouble`, inheriting from `AtomicNumber[T]`):
 * {{{
 *   val atomicNumber = Atomic(12.2)
 *
 *   atomicNumber.incrementAndGet()
 *   // => 13.2
 * }}}
 *
 * In comparison with `java.util.concurrent.AtomicReference`, these references implement common interfaces
 * that you can use generically (i.e. `Atomic[T]`, `AtomicNumber[T]`, `BlockableAtomic[T]`, `WeakAtomic[T]`).
 * And also provide useful helpers for atomically mutating of values
 * (i.e. `transform`, `transformAndGet`, `getAndTransform`, etc...) or of numbers of any kind
 * (`incrementAndGet`, `getAndAdd`, etc...).
 *
 * A high-level documentation describing the rationale for these can be found here:
 * [[https://github.com/alexandru/monix/blob/master/docs/atomic.md Atomic Reference]]
 */
package object atomic {
  /**
   * For private use only by the `monix` package.
   *
   * Checks if the current thread has been interrupted, throwing
   * an `InterruptedException` in case it is.
   */
  @inline private[atomic] def interruptedCheck(): Unit = {
    if (Thread.interrupted)
      throw new InterruptedException()
  }

  /**
   * For private use only by the `monix` package.
   *
   * Checks if the timeout is due, throwing a `TimeoutException` in case it is.
   */
  @inline private[atomic] def timeoutCheck(endsAtNanos: Long): Unit = {
    if (System.nanoTime >= endsAtNanos)
      throw new TimeoutException()
  }
}
