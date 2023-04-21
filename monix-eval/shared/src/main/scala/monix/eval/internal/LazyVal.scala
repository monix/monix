/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.eval.internal

import monix.eval.Coeval
import monix.eval.Coeval.{ Error, Now }
import scala.util.control.NonFatal

/** `LazyVal` boxes a function and memoizes its result on its first invocation,
  * to be reused for subsequent invocations.
  *
  * It is the equivalent of Scala's `lazy val`, but can (optionally)
  * cache the result only on successful completion.
  *
  * Guarantees thread-safe idempotency for successful results and
  * also for failures when `onlyOnSuccess = false`.
  *
  * @param f is the function whose result is going to get memoized
  * @param cacheErrors is a boolean that indicates whether
  *        memoization should happen only for successful results,
  *        or for errors as well
  */
private[eval] final class LazyVal[A] private (f: () => A, val cacheErrors: Boolean) extends (() => Coeval.Eager[A]) {

  private[this] var thunk = f
  private[this] var cache: Coeval.Eager[A] = _

  override def apply(): Coeval.Eager[A] =
    cache match {
      case null => compute()
      case ref => ref
    }

  private def compute(): Coeval.Eager[A] =
    synchronized {
      // Double-check due to possible race condition;
      // this being the double-checked locking
      if (cache ne null) {
        // $COVERAGE-OFF$
        cache
        // $COVERAGE-ON$
      } else {
        try {
          cache = Now(thunk())
          thunk = null
          cache
        } catch {
          case NonFatal(e) if cacheErrors =>
            cache = Error(e)
            thunk = null
            throw e
        }
      }
    }
}

private[eval] object LazyVal {
  /** Builder. */
  def apply[A](f: () => A, cacheErrors: Boolean): (() => Coeval.Eager[A]) =
    new LazyVal[A](f, cacheErrors)
}
