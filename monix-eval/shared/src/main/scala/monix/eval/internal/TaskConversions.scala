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

import cats.effect.{Effect, IO}
import monix.eval.Task
import monix.eval.instances.CatsBaseForTask
import monix.execution.internal.AttemptCallback
import monix.execution.misc.NonFatal
import monix.execution.{CancelableFuture, Scheduler}

import scala.util.{Failure, Success}

private[eval] object TaskConversions {
  /** Implementation for `Task#toIO`. */
  def toIO[A](source: Task[A])(implicit s: Scheduler): IO[A] = {
    def suspend(task: Task[A]): IO[A] =
      IO.suspend {
        val f = task.runAsync(s)
        f.value match {
          case Some(value) =>
            value match {
              case Success(a) => IO.pure(a)
              case Failure(e) => IO.raiseError(e)
            }
          case None =>
            async(f)
        }
      }

    def async(f: CancelableFuture[A]): IO[A] =
      IO.cancelable { cb =>
        f.underlying.onComplete(AttemptCallback.toTry(cb))
        f.cancelable.cancelIO
      }

    source match {
      case Task.Now(v) => IO.pure(v)
      case Task.Error(e) => IO.raiseError(e)
      case Task.Eval(thunk) => IO(thunk())
      case Task.Suspend(thunk) => IO.suspend(toIO(thunk()))
      case other => suspend(other)
    }
  }

  /** Implementation for `Task#fromIO`. */
  def fromIO[A](io: IO[A]): Task[A] =
    io.to[Task]

  /** Implementation for `Task#fromEffect`. */
  def fromEffect[F[_], A](fa: F[A])(implicit F: Effect[F]): Task[A] = {
    import IO.ioEffect
    F match {
      case _: CatsBaseForTask =>
        fa.asInstanceOf[Task[A]]
      case `ioEffect` =>
        fromIO(fa.asInstanceOf[IO[A]])
      case _ =>
        Task.unsafeCreate { (ctx, cb) =>
          try {
            val io = F.runAsync(fa) {
              case Right(a) => cb.onSuccess(a); IO.unit
              case Left(e) => cb.onError(e); IO.unit
            }
            io.unsafeRunAsync(unitCb)
          } catch {
            case e if NonFatal(e) =>
              ctx.scheduler.reportFailure(e)
          }
        }
    }
  }

  // Reusable instance to avoid extra allocations
  private final val unitCb: (Either[Throwable, Unit] => Unit) =
    _ => ()
}
