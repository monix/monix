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

package monix.eval.internal

import java.util.concurrent.CancellationException

import monix.eval.Task
import monix.execution.UncaughtExceptionReporter
import monix.execution.misc.NonFatal

private[eval] object TaskBracket {
  /**
    * Implementation for `Task.bracketE`.
    */
  def apply[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, Either[Option[Throwable], B]) => Task[Unit]): Task[B] = {

    acquire.flatMap { a =>
      val next = try use(a) catch { case NonFatal(e) => Task.raiseError(e) }
      next.onCancelRaiseError(isCancel).flatMap(new ReleaseFrame(a, release))
    }
  }

  private final class ReleaseFrame[A, B](
    a: A,
    release: (A, Either[Option[Throwable], B]) => Task[Unit])
    extends StackFrame[B, Task[B]] {

    def apply(b: B): Task[B] =
      release(a, Right(b)).map(_ => b)

    def recover(e: Throwable, r: UncaughtExceptionReporter): Task[B] = {
      if (e ne isCancel)
        release(a, Left(Some(e))).flatMap(new ReleaseRecover(e, r))
      else
        release(a, leftNone).flatMap(neverFn)
    }
  }

  private final class ReleaseRecover(e: Throwable, r: UncaughtExceptionReporter)
    extends StackFrame[Unit, Task[Nothing]] {

    def apply(a: Unit): Task[Nothing] =
      Task.raiseError(e)

    def recover(e2: Throwable, r: UncaughtExceptionReporter): Task[Nothing] = {
      r.reportFailure(e2)
      Task.raiseError(e)
    }
  }

  private final val isCancel = new CancellationException("bracket")
  private final val neverFn = (_: Unit) => Task.never[Nothing]
  private final val leftNone = Left(None)
}
