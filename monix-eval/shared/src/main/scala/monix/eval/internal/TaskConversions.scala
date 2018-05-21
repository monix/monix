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

import cats.effect._
import monix.eval.Task.Context
import monix.eval.{Callback, Task}
import monix.execution.cancelables.{SingleAssignCancelable, StackedCancelable}
import monix.execution.internal.AttemptCallback
import scala.util.control.NonFatal
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.execution.schedulers.TrampolinedRunnable

import scala.util.{Failure, Success}

private[eval] object TaskConversions {
  /** Implementation for `Task#to`. */
  def to[F[_], A](source: Task[A])(implicit F: Async[F], s: Scheduler): F[A] = {
    def suspend(task: Task[A])(implicit F: Async[F]): F[A] =
      F.suspend {
        val f = task.runAsync(s)
        f.value match {
          case Some(value) =>
            value match {
              case Success(a) => F.pure(a)
              case Failure(e) => F.raiseError(e)
            }
          case None =>
            F match {
              case ref: Concurrent[F] @unchecked =>
                cancelable(f)(ref)
              case _ =>
                async(f)
            }
        }
      }

    def async(f: CancelableFuture[A])(implicit F: Async[F]): F[A] =
      F.async { cb =>
        f.underlying.onComplete(AttemptCallback.toTry(cb))(immediate)
      }

    def cancelable(f: CancelableFuture[A])(implicit F: Concurrent[F]): F[A] =
      F.cancelable { cb =>
        f.underlying.onComplete(AttemptCallback.toTry(cb))(immediate)
        f.cancelable.cancelIO
      }

    source match {
      case Task.Now(v) => F.pure(v)
      case Task.Error(e) => F.raiseError(e)
      case Task.Eval(thunk) => F.delay(thunk())
      case Task.Suspend(thunk) => F.suspend(to(thunk()))
      case other => suspend(other)(F)
    }
  }

  /** Implementation for `Task.from`. */
  def from[F[_], A](fa: F[A])(implicit F: Effect[F]): Task[A] =
    fa.asInstanceOf[AnyRef] match {
      case ref: Task[A] @unchecked => ref
      case io: IO[A] @unchecked => io.to[Task]
      case _ =>
        F match {
          case ref: ConcurrentEffect[F] @unchecked =>
            fromConcurrent0(fa)(ref)
          case _ =>
            fromAsync0(fa)(F)
        }
    }

  private def fromAsync0[F[_], A](fa: F[A])(implicit F: Effect[F]): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      try {
        val io = F.runAsync(fa)(new CreateCallback[A](null, cb)(ctx.scheduler))
        io.unsafeRunAsync(AttemptCallback.noop)
      } catch {
        case e if NonFatal(e) =>
          ctx.scheduler.reportFailure(e)
      }
    }
    Task.Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  private def fromConcurrent0[F[_], A](fa: F[A])(implicit F: ConcurrentEffect[F]): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      try {
        implicit val sc = ctx.scheduler
        val conn = ctx.connection
        val cancelable = SingleAssignCancelable()
        conn push cancelable

        val io = F.runCancelable(fa)(new CreateCallback[A](conn, cb))
        cancelable := Cancelable.fromIOUnsafe(io.unsafeRunSync())
      } catch {
        case e if NonFatal(e) =>
          ctx.scheduler.reportFailure(e)
      }
    } : Unit

    Task.Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  private final class CreateCallback[A](
    conn: StackedCancelable, cb: Callback[A])
    (implicit s: Scheduler)
    extends (Either[Throwable, A] => IO[Unit]) with TrampolinedRunnable {

    private[this] var canCall = true
    private[this] var value: Either[Throwable, A] = _

    def run(): Unit = {
      if (canCall) {
        canCall = false
        if (conn ne null) conn.pop()
        cb(value)
        value = null
      }
    }

    override def apply(value: Either[Throwable, A]) =
      IO {
        this.value = value
        s.execute(this)
      }
  }
}
