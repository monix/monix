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

package monix.eval
package internal

import monix.execution.UncaughtExceptionReporter
import monix.execution.misc.NonFatal

private[eval] object CoevalBracket {
  /**
    * Implementation for `Coeval.bracketE`.
    */
  def apply[A, B](
    acquire: Coeval[A],
    use: A => Coeval[B],
    release: (A, Either[Throwable, B]) => Coeval[Unit]): Coeval[B] = {

    acquire.flatMap { a =>
      val next = try use(a) catch { case NonFatal(e) => Coeval.raiseError(e) }
      next.flatMap(new ReleaseFrame(a, release))
    }
  }

  private final class ReleaseFrame[A, B](
    a: A,
    release: (A, Either[Throwable, B]) => Coeval[Unit])
    extends StackFrame[B, Coeval[B]] {

    def apply(b: B): Coeval[B] =
      release(a, Right(b)).map(_ => b)

    def recover(e: Throwable, r: UncaughtExceptionReporter): Coeval[B] =
      release(a, Left(e)).flatMap(new ReleaseRecover(e, r))
  }

  private final class ReleaseRecover(e: Throwable, r: UncaughtExceptionReporter)
    extends StackFrame[Unit, Coeval[Nothing]] {

    def apply(a: Unit): Coeval[Nothing] =
      Coeval.raiseError(e)

    def recover(e2: Throwable, r: UncaughtExceptionReporter): Coeval[Nothing] = {
      r.reportFailure(e2)
      Coeval.raiseError(e)
    }
  }
}
