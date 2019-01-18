/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.eval.Task.{Context, ContextSwitch}
import monix.execution.Callback
import monix.eval.Task
import monix.execution.atomic.Atomic
import monix.execution.internal.Platform
import scala.util.control.NonFatal

private[monix] object TaskBracket {

  // -----------------------------------------------------------------
  // Task.bracketE
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  /**
    * [[monix.eval.Task.bracket]] and [[monix.eval.Task.bracketCase]]
    */
  def either[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, Either[Option[Throwable], B]) => Task[Unit]): Task[B] = {

    Task.Async(
      new StartE(acquire, use, release),
      trampolineBefore = true,
      trampolineAfter = true,
      restoreLocals = false)
  }

  private final class StartE[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, Either[Option[Throwable], B]) => Task[Unit])
    extends BaseStart(acquire, use) {

    def makeReleaseFrame(ctx: Context, value: A) =
      new ReleaseFrameE(ctx, value, release)
  }

  private final class ReleaseFrameE[A, B](
    ctx: Context,
    a: A,
    release: (A, Either[Option[Throwable], B]) => Task[Unit])
    extends BaseReleaseFrame[A, B](ctx, a) {

    def releaseOnSuccess(a: A, b: B): Task[Unit] =
      release(a, Right(b))
    def releaseOnError(a: A, e: Throwable): Task[Unit] =
      release(a, Left(Some(e)))
    def releaseOnCancel(a: A): Task[Unit] =
      release(a, leftNone)
  }

  // -----------------------------------------------------------------
  // Task.bracketCase
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  /**
    * [[monix.eval.Task.bracketE]]
    */
  def exitCase[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] = {

    Task.Async(
      new StartCase(acquire, use, release),
      trampolineBefore = true,
      trampolineAfter = true,
      restoreLocals = false)
  }

  private final class StartCase[A, B](
    acquire: Task[A],
    use: A => Task[B],
    release: (A, ExitCase[Throwable]) => Task[Unit])
    extends BaseStart(acquire, use) {

    def makeReleaseFrame(ctx: Context, value: A) =
      new ReleaseFrameCase(ctx, value, release)
  }

  private final class ReleaseFrameCase[A, B](
    ctx: Context,
    a: A,
    release: (A, ExitCase[Throwable]) => Task[Unit])
    extends BaseReleaseFrame[A, B](ctx, a) {

    def releaseOnSuccess(a: A, b: B): Task[Unit] =
      release(a, Completed)
    def releaseOnError(a: A, e: Throwable): Task[Unit] =
      release(a, Error(e))
    def releaseOnCancel(a: A): Task[Unit] =
      release(a, Canceled)
  }

  // -----------------------------------------------------------------
  // Base Implementation
  // =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  private abstract class BaseStart[A, B](
    acquire: Task[A],
    use: A => Task[B])
    extends ((Context, Callback[Throwable, B]) => Unit) {

    protected def makeReleaseFrame(ctx: Context, value: A): BaseReleaseFrame[A, B]

    final def apply(ctx: Context, cb: Callback[Throwable, B]): Unit = {
      // Async boundary needed, but it is guaranteed via Task.Async below;
      Task.unsafeStartNow(acquire, ctx.withConnection(TaskConnection.uncancelable),
        new Callback[Throwable, A] {
          def onSuccess(value: A): Unit = {
            implicit val sc = ctx.scheduler
            val conn = ctx.connection

            val releaseFrame = makeReleaseFrame(ctx, value)
            val onNext = {
              val fb = try use(value) catch { case NonFatal(e) => Task.raiseError(e) }
              fb.flatMap(releaseFrame)
            }

            conn.push(releaseFrame.cancel)
            Task.unsafeStartNow(onNext, ctx, cb)
          }

          def onError(ex: Throwable): Unit =
            cb.onError(ex)
        })
    }
  }

  private abstract class BaseReleaseFrame[A, B](ctx: Context, a: A)
    extends StackFrame[B, Task[B]] {

    private[this] val waitsForResult = Atomic(true)

    protected def releaseOnSuccess(a: A, b: B): Task[Unit]
    protected def releaseOnError(a: A, e: Throwable): Task[Unit]
    protected def releaseOnCancel(a: A): Task[Unit]

    private final def applyEffect(b: B): Task[B] = {
      if (waitsForResult.compareAndSet(expect = true, update = false)) {
        ctx.connection.pop()
        releaseOnSuccess(a, b).map(_ => b)
      } else {
        Task.never
      }
    }

    final def apply(b: B): Task[B] = {
      val task =
        if (ctx.options.autoCancelableRunLoops)
          Task.suspend(applyEffect(b))
        else
          applyEffect(b)

      ContextSwitch(task, withConnectionUncancelable, null)
    }

    private final def recoverEffect(e: Throwable): Task[B] = {
      if (waitsForResult.compareAndSet(expect = true, update = false)) {
        ctx.connection.pop()
        releaseOnError(a, e).flatMap(new ReleaseRecover(e))
      } else {
        Task.never
      }
    }

    final def recover(e: Throwable): Task[B] = {
      val task =
        if (ctx.options.autoCancelableRunLoops)
          Task.suspend(recoverEffect(e))
        else
          recoverEffect(e)

      ContextSwitch(task, withConnectionUncancelable, null)
    }

    final def cancel: Task[Unit] =
      Task.suspend {
        if (waitsForResult.compareAndSet(expect = true, update = false))
          releaseOnCancel(a)
        else
          Task.unit
      }
  }

  private final class ReleaseRecover(e: Throwable)
    extends StackFrame[Unit, Task[Nothing]] {

    def apply(a: Unit): Task[Nothing] =
      Task.raiseError(e)

    def recover(e2: Throwable): Task[Nothing] =
      Task.raiseError(Platform.composeErrors(e, e2))
  }

  private val leftNone = Left(None)

  private[this] val withConnectionUncancelable: Context => Context =
    _.withConnection(TaskConnection.uncancelable)
}
