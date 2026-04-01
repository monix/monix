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

package monix.reactive.internal.deprecated

import scala.annotation.nowarn
import monix.execution.Scheduler
import monix.reactive.subjects.ConcurrentSubject

/** Binary-compatibility shims for [[monix.reactive.subjects.ConcurrentSubject]].
  *
  * These methods preserve the binary interface of `ConcurrentSubject` from 3.4.0.
  * They are package-private and must not be called by user code.
  */
@nowarn("msg=Implicit parameters should be provided with a `using` clause")
private[monix] trait ConcurrentSubjectDeprecatedBuilders extends Any {

  /** Binary-compatibility shim — the `Scheduler` parameter is no longer needed.
    *
    * In 3.4.0, `async` required an implicit `Scheduler`; in 3.5.0 it does not.
    * This overload preserves the old JVM bytecode descriptor so that pre-compiled
    * 3.4.0 call-sites remain link-compatible at runtime.
    *
    * @deprecated Use [[ConcurrentSubject.async[A]:* ConcurrentSubject.async]] instead.
    */
  @deprecated("The Scheduler parameter is no longer needed; use ConcurrentSubject.async without it.", "3.5.0")
  private[monix] def async[A](implicit s: Scheduler): ConcurrentSubject[A, A] =
    // $COVERAGE-OFF$
    ConcurrentSubject.async[A]
    // $COVERAGE-ON$
}
