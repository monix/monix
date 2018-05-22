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

import cats.effect.ExitCase
import cats.effect.ExitCase.{Canceled, Completed, Error}
import monix.eval.{Callback, Task}
import monix.eval.Task.{Context, ContextSwitch}
import monix.execution.{Cancelable, Scheduler}
import monix.execution.atomic.{Atomic, AtomicBoolean}
import monix.execution.cancelables.StackedCancelable
import monix.execution.internal.Platform

private[monix] object TaskBracket {
  // -----------------------------------------------------------------
  // Task.bracketCase
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  /**
    * Implementation for `Task#bracket`.
    */
  def exitCase[A, B](acquire: Task[A], use: A => Task[B], release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
    Task.Suspend(new CaseStart(acquire, use, release))

  private final class CaseStart[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, ExitCase[Throwable]) => Task[Unit])
    extends BaseStart[A, B](acquire, use) {

    def makeFrame(isActive: AtomicBoolean, success: A): BaseFrameCase[A, B] =
      new CaseFrame(isActive, success, release)

    def makeCancelable(isActive: AtomicBoolean, a: A)
      (implicit s: Scheduler): Cancelable =
      new CaseCancelable(isActive, a, release)
  }

  private final class CaseFrame[A, B](
    isActive: AtomicBoolean,
    resource: A,
    finalizer: (A, ExitCase[Throwable]) => Task[Unit])
    extends BaseFrameCase[A, B](isActive) {

    def onSuccess(result: B): Task[Unit] =
      finalizer(resource, Completed)
    def onError(error: Throwable): Task[Unit] =
      finalizer(resource, Error(error))
  }

  private final class CaseCancelable[A](
    isActive: AtomicBoolean,
    value: A,
    finalizer: (A, ExitCase[Throwable]) => Task[Unit])
    (implicit s: Scheduler)
    extends Cancelable {

    def cancel(): Unit = {
      // Race condition with FrameCase described above
      if (isActive.getAndSet(false))
        finalizer(value, exitCaseCanceled).runAsync(Callback.empty)
    }
  }

  // -----------------------------------------------------------------
  // Task.bracketCase
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  /**
    * Implementation for `Task#bracketE`.
    */
  def either[A, B](acquire: Task[A], use: A => Task[B], release: (A, Either[Option[Throwable], B]) => Task[Unit]): Task[B] =
    Task.Suspend(new EitherStart(acquire, use, release))

  private final class EitherStart[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, Either[Option[Throwable], B]) => Task[Unit])
    extends BaseStart[A, B](acquire, use) {

    def makeFrame(isActive: AtomicBoolean, success: A): BaseFrameCase[A, B] =
      new EitherFrame(isActive, success, release)

    def makeCancelable(isActive: AtomicBoolean, a: A)
      (implicit s: Scheduler): Cancelable =
      new EitherCancelable(isActive, a, release)
  }

  private final class EitherFrame[A, B](
    isActive: AtomicBoolean,
    resource: A,
    finalizer: (A, Either[Option[Throwable], B]) => Task[Unit])
    extends BaseFrameCase[A, B](isActive) {

    def onSuccess(result: B): Task[Unit] =
      finalizer(resource, Right(result))
    def onError(error: Throwable): Task[Unit] =
      finalizer(resource, Left(Some(error)))
  }

  private final class EitherCancelable[A, B](
    isActive: AtomicBoolean,
    value: A,
    finalizer: (A, Either[Option[Throwable], B]) => Task[Unit])
    (implicit s: Scheduler)
    extends Cancelable {

    def cancel(): Unit = {
      // Race condition with FrameCase described above
      if (isActive.getAndSet(false))
        finalizer(value, eitherCanceled).runAsync(Callback.empty)
    }
  }

  // -----------------------------------------------------------------
  // Base implementation
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  private abstract class BaseStart[A, B](
    acquire: Task[A],
    use: A => Task[B])
    extends Function0[Task[B]] {

    protected def makeFrame(isActive: AtomicBoolean, a: A): BaseFrameCase[A, B]

    protected def makeCancelable(isActive: AtomicBoolean, a: A)
      (implicit s: Scheduler): Cancelable

    final def apply(): Task[B] = {
      // Value used to synchronize between `flatMap` and `cancel`, ensuring
      // idempotency in evaluating `release`
      val isActive = Atomic(true)

      // Makes `acquire` execute in `uncancelable` mode, but then `uncancelable`
      // is turned off right before evaluating `use`
      val acquireTask = ContextSwitch(
        acquire,
        withConnectionUncancelable,
        // Temporary release
        (success: A, error: Throwable, oldCtx, currentCtx) => {
          if (error ne null)
            currentCtx.withConnection(oldCtx.connection)
          else {
            // Restoring old connection, but also push new cancelable immediately;
            // this will ensure a lean transition between `acquire` and `use`
            val ctx = currentCtx.withConnection(oldCtx.connection)
            ctx.connection.push(makeCancelable(isActive, success)(ctx.scheduler))
            ctx
          }
        })

      // Shouldn't be a problem if this `flatMap` gets interrupted, because
      // we've already registered the cancelable in `acquireTask` above
      acquireTask.flatMap { a =>
        // FrameCase is in charge of `release`, if not canceled
        use(a).flatMap(makeFrame(isActive, a))
      }
    }
  }

  private abstract class BaseFrameCase[A, B](isActive: AtomicBoolean)
    extends StackFrame[B, Task[B]] {

    protected def onSuccess(result: B): Task[Unit]
    protected def onError(error: Throwable): Task[Unit]

    private final def protect(task: Task[B]): Task[B] =
      Task.suspend {
        // Finalizer is made uncancelable; this means that by the time this
        // `isActive` is set to `false`, the connection is in uncancelable mode
        // already, which means that it is safe to execute
        if (isActive.getAndSet(false)) task
        else Task.never
      }

    final def apply(b: B): Task[B] =
      ContextSwitch(
        protect(onSuccess(b).map(_ => b)),
        // Gets rid of `ReleaseCancelable`, but enters uncancelable mode
        withConnectionPopThenUncancelable,
        // Finally restores connection to the initial one
        restoreConnection)

    final def recover(e: Throwable): Task[B] =
      ContextSwitch(
        protect(onError(e).flatMap(new ReleaseFrameError(e))),
        // Gets rid of `ReleaseCancelable`, but enters uncancelable mode
        withConnectionPopThenUncancelable,
        // Finally restores connection to the initial one
        restoreConnection)
  }

  private final class ReleaseFrameError[B](error: Throwable)
    extends StackFrame[Unit, Task[B]] {

    def apply(unit: Unit): Task[B] =
      Task.Error(error)

    def recover(e: Throwable): Task[B] =
      Task.Error(Platform.composeErrors(error, e))
  }

  private[this] val exitCaseCanceled: ExitCase[Throwable] =
    Canceled(None)

  private[this] val eitherCanceled =
    Left(None)

  private[this] val withConnectionUncancelable: Context => Context =
    _.withConnection(StackedCancelable.uncancelable)

  private[this] val withConnectionPopThenUncancelable: Context => Context =
    ct => {
      ct.connection.pop()
      ct.withConnection(StackedCancelable.uncancelable)
    }

  private[this] val restoreConnection: (Any, Throwable, Context, Context) => Context =
    (_, _, old, current) => current.withConnection(old.connection)
}
