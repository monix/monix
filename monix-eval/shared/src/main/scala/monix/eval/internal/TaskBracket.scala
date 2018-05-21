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
import cats.effect.ExitCase.{Completed, Error}
import monix.eval.Task.Context
import monix.eval.{Callback, Task}
import monix.execution.Cancelable
import monix.execution.atomic.Atomic
import monix.execution.internal.Platform
import scala.util.control.NonFatal

private[eval] object TaskBracket {
  /**
    * Implementation for `Task.bracketE`.
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

  /**
    * Implementation for `Task.bracketCase`.
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

  private abstract class BaseStart[A, B](
    acquire: Task[A],
    use: A => Task[B])
    extends ((Context, Callback[B]) => Unit) {

    protected def makeReleaseFrame(ctx: Context, value: A): BaseReleaseFrame[A, B]

    final def apply(ctx: Context, cb: Callback[B]): Unit = {
      // Async boundary needed, but it is guaranteed via Task.Async below
      Task.unsafeStartNow(acquire, ctx, new Callback[A] {
        def onSuccess(value: A): Unit = {
          implicit val sc = ctx.scheduler
          val conn = ctx.connection

          val releaseFrame = makeReleaseFrame(ctx, value)
          val onNext = {
            val fb = try use(value) catch { case NonFatal(e) => Task.raiseError(e) }
            fb.flatMap(releaseFrame)
          }

          conn.push(releaseFrame)
          Task.unsafeStartNow(onNext, ctx, cb)
        }

        def onError(ex: Throwable): Unit =
          cb.onError(ex)
      })
    }
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
      release(a, canceled)
  }


  private abstract class BaseReleaseFrame[A, B](ctx: Context, a: A)
    extends StackFrame[B, Task[B]] with Cancelable {

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
      if (ctx.options.autoCancelableRunLoops)
        Task.suspend(applyEffect(b)).uncancelable
      else
        applyEffect(b)
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
      if (ctx.options.autoCancelableRunLoops)
        Task.suspend(recoverEffect(e)).uncancelable
      else
        recoverEffect(e)
    }

    final def cancel(): Unit =
      if (waitsForResult.compareAndSet(expect = true, update = false)) {
        implicit val ec = ctx.scheduler
        try {
          val task = releaseOnCancel(a)
          task.runAsync(Callback.empty)
        } catch {
          case NonFatal(e) =>
            ec.reportFailure(e)
        }
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
  private val canceled = ExitCase.canceled[Throwable]
}
